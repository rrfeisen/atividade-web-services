import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente de uma API REST pública (Open-Meteo) que devolve JSON.
 *
 * Fluxo:
 *   1. GET geocoding-api.open-meteo.com/v1/search  -> latitude/longitude da cidade
 *   2. GET api.open-meteo.com/v1/forecast          -> clima atual naquela coordenada
 *
 * Executar (Java 11+, sem dependências):
 *   java ClimaRest.java "Montes Claros"
 *   java ClimaRest.java "Montes Claros" --raw      (mostra também o JSON bruto)
 */
public class ClimaRest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: java ClimaRest.java \"<cidade>\" [--raw]");
            return;
        }
        String cidade = args[0];
        boolean raw = args.length > 1 && args[1].equals("--raw");

        // 1) Geocoding: cidade -> coordenadas
        String urlGeo = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(cidade, StandardCharsets.UTF_8)
                + "&count=1&language=pt&format=json";
        String jsonGeo = get(urlGeo);
        if (raw) System.out.println("=== JSON GEOCODING ===\n" + jsonGeo + "\n");

        Map<String, Object> geo = asMap(new Json(jsonGeo).parse());
        List<Object> resultados = asList(geo.get("results"));
        if (resultados == null || resultados.isEmpty()) {
            System.out.println("Cidade não encontrada: " + cidade);
            return;
        }
        Map<String, Object> local = asMap(resultados.get(0));
        double lat = ((Number) local.get("latitude")).doubleValue();
        double lon = ((Number) local.get("longitude")).doubleValue();

        // 2) Clima atual
        String urlClima = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code"
                + "&timezone=auto";
        String jsonClima = get(urlClima);
        if (raw) System.out.println("=== JSON CLIMA ===\n" + jsonClima + "\n");

        Map<String, Object> clima = asMap(new Json(jsonClima).parse());
        Map<String, Object> atual = asMap(clima.get("current"));
        Map<String, Object> unid = asMap(clima.get("current_units"));

        System.out.println("Local ........: " + local.get("name") + " - " + local.get("admin1")
                + " (" + local.get("country") + ")");
        System.out.println("Coordenadas ..: " + lat + ", " + lon);
        System.out.println("Horário ......: " + atual.get("time"));
        System.out.println("Condição .....: " + descricao(((Number) atual.get("weather_code")).intValue()));
        System.out.println("Temperatura ..: " + atual.get("temperature_2m") + " " + unid.get("temperature_2m"));
        System.out.println("Sensação .....: " + atual.get("apparent_temperature") + " " + unid.get("apparent_temperature"));
        System.out.println("Umidade ......: " + atual.get("relative_humidity_2m") + " " + unid.get("relative_humidity_2m"));
        System.out.println("Vento ........: " + atual.get("wind_speed_10m") + " " + unid.get("wind_speed_10m"));
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " em " + url + "\n" + resp.body());
        }
        return resp.body();
    }

    /** Códigos WMO do Open-Meteo -> texto. */
    private static String descricao(int code) {
        return switch (code) {
            case 0 -> "Céu limpo";
            case 1, 2, 3 -> "Parcialmente nublado / nublado";
            case 45, 48 -> "Neblina";
            case 51, 53, 55, 56, 57 -> "Garoa";
            case 61, 63, 65, 66, 67 -> "Chuva";
            case 71, 73, 75, 77 -> "Neve";
            case 80, 81, 82 -> "Pancadas de chuva";
            case 95, 96, 99 -> "Tempestade";
            default -> "Código WMO " + code;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) { return (List<Object>) o; }

    /** Parser JSON mínimo (objetos, arrays, strings, números, true/false/null). */
    static class Json {
        private final String s;
        private int i = 0;

        Json(String s) { this.s = s; }

        Object parse() {
            Object v = value();
            ws();
            return v;
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws(); i++; // ':'
                m.put(k, value());
                ws();
                if (s.charAt(i++) == '}') return m;
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i++) == ']') return l;
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // abre aspas
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
            i++; // fecha aspas
            return sb.toString();
        }

        private Number number() {
            int ini = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(ini, i);
            return (n.contains(".") || n.contains("e") || n.contains("E")) ? (Number) Double.valueOf(n) : (Number) Long.valueOf(n);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
