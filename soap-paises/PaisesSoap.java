import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Cliente de um Web Service SOAP público (CountryInfoService, da Oorsprong).
 *
 * WSDL: http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL
 *
 * Operações consumidas (todas definidas no WSDL, SOAP 1.1):
 *   - CountryName(sCountryISOCode)     -> nome do país
 *   - CapitalCity(sCountryISOCode)     -> capital
 *   - CountryCurrency(sCountryISOCode) -> moeda (código + nome)
 *   - CountryIntPhoneCode(...)         -> código telefônico internacional
 *
 * O envelope SOAP é montado manualmente (XML) e enviado por HTTP POST,
 * para deixar explícito o que trafega na rede.
 *
 * Executar (Java 11+, sem dependências):
 *   java PaisesSoap.java BR
 *   java PaisesSoap.java BR --raw     (mostra os XMLs de requisição/resposta)
 */
public class PaisesSoap {

    private static final String ENDPOINT = "http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso";
    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static boolean raw;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: java PaisesSoap.java <codigo-ISO-2-letras> [--raw]   (ex.: BR, US, JP)");
            return;
        }
        String iso = args[0].toUpperCase();
        raw = args.length > 1 && args[1].equals("--raw");

        String nome = chamar("CountryName", "sCountryISOCode", iso, "CountryNameResult");
        if (nome.isBlank() || nome.toLowerCase().contains("not found")) {
            System.out.println("Código de país não encontrado: " + iso);
            return;
        }
        String capital = chamar("CapitalCity", "sCountryISOCode", iso, "CapitalCityResult");
        String telefone = chamar("CountryIntPhoneCode", "sCountryISOCode", iso, "CountryIntPhoneCodeResult");
        Document moeda = chamarDoc("CountryCurrency", "sCountryISOCode", iso);

        System.out.println("Código ISO ...: " + iso);
        System.out.println("País .........: " + nome);
        System.out.println("Capital ......: " + capital);
        System.out.println("DDI ..........: +" + telefone);
        System.out.println("Moeda ........: " + texto(moeda, "sISOCode") + " - " + texto(moeda, "sName"));
    }

    /** Chama uma operação SOAP e devolve o texto do elemento de resultado. */
    private static String chamar(String operacao, String param, String valor, String resultado) throws Exception {
        return texto(chamarDoc(operacao, param, valor), resultado);
    }

    private static Document chamarDoc(String operacao, String param, String valor) throws Exception {
        String envelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <%1$s xmlns="%2$s">
                      <%3$s>%4$s</%3$s>
                    </%1$s>
                  </soap:Body>
                </soap:Envelope>""".formatted(operacao, NS, param, valor);

        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", NS + "/" + operacao)
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        String corpo = new String(resp.body(), StandardCharsets.UTF_8);
        if (raw) {
            System.out.println("=== REQUISIÇÃO SOAP (" + operacao + ") ===\n" + envelope + "\n");
            System.out.println("=== RESPOSTA SOAP (HTTP " + resp.statusCode() + ") ===\n" + formatar(corpo) + "\n");
        }
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " (SOAP Fault?)\n" + corpo);
        }
        return parse(corpo);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Primeiro elemento com o nome local informado (ignora prefixo/namespace). */
    private static String texto(Document d, String nomeLocal) {
        NodeList l = d.getElementsByTagNameNS("*", nomeLocal);
        if (l.getLength() == 0) return "";
        Node n = l.item(0);
        return n.getTextContent().trim();
    }

    private static String formatar(String xml) {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(parse(xml)), new StreamResult(w));
            return w.toString();
        } catch (Exception e) {
            return xml;
        }
    }
}
