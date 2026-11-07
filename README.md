# Atividade Web Services — REST (JSON) e SOAP (XML)

**Alunos:** João Vitor Alves Medina e Roberto Ramos Ferreira

**Disciplina:** Sistemas Distribuídos

Este repositório contém duas aplicações independentes em **Java** (sem bibliotecas externas, só a JDK ≥ 11; testado com JDK 25):

| Pasta | Tipo | Serviço consumido | Tema |
|---|---|---|---|
| [`rest-clima/`](rest-clima/ClimaRest.java) | API REST (JSON) | Open-Meteo (Geocoding + Forecast) | Clima atual de uma cidade |
| [`soap-paises/`](soap-paises/PaisesSoap.java) | Web Service SOAP (XML/WSDL) | CountryInfoService (Oorsprong) | Dados de um país (nome, capital, DDI, moeda) |

---

## A. Aplicação REST — como executar

Requisitos: JDK 11+ e acesso à internet (não precisa de chave de API).

```powershell
cd rest-clima
java ClimaRest.java "Montes Claros"          # resultado formatado
java ClimaRest.java "Montes Claros" --raw    # também imprime os JSONs recebidos
```

Saída real:

```text
Local ........: Montes Claros - Minas Gerais (Brasil)
Coordenadas ..: -16.735, -43.86167
Horário ......: 2026-11-07T11:45
Condição .....: Parcialmente nublado / nublado
Temperatura ..: 32.0 °C
Sensação .....: 36.9 °C
Umidade ......: 48 %
Vento ........: 8.7 km/h
```

Funcionamento: (1) `GET /v1/search?name=<cidade>` obtém latitude/longitude; (2) `GET /v1/forecast?latitude=..&longitude=..&current=...` obtém o clima atual. O JSON é lido por um parser próprio, pequeno, embutido no arquivo.

## B. Aplicação SOAP — como executar

Requisitos: JDK 11+ e acesso à internet.

```powershell
cd soap-paises
java PaisesSoap.java BR           # resultado formatado
java PaisesSoap.java US --raw     # também imprime os XMLs de requisição e resposta
```

Saída real (`BR`):

```text
Código ISO ...: BR
País .........: Brazil
Capital ......: Brasilia
DDI ..........: +55
Moeda ........: BRL - Brazil Real
```

Funcionamento: o envelope SOAP 1.1 é montado como texto XML e enviado por `HTTP POST` (cabeçalhos `Content-Type: text/xml` e `SOAPAction`) ao endpoint do serviço. A resposta é lida com o parser DOM da JDK. São chamadas 4 operações do WSDL: `CountryName`, `CapitalCity`, `CountryIntPhoneCode` e `CountryCurrency`.

> **Dica (Windows):** para acentos corretos no terminal, rode antes `chcp 65001`.

---

## C. Relatório Técnico

### a. Documentações oficiais das APIs

- **Open-Meteo Forecast API:** https://open-meteo.com/en/docs
- **Open-Meteo Geocoding API:** https://open-meteo.com/en/docs/geocoding-api
- **CountryInfoService (SOAP):** http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso (página do serviço, com a descrição de cada operação)

### b. Link direto do arquivo `.wsdl` utilizado

http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL

Endpoint de chamada: `http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso`
Namespace: `http://www.oorsprong.org/websamples.countryinfo`

### c. Exemplo real do JSON recebido pela aplicação REST

Resposta de `https://api.open-meteo.com/v1/forecast?latitude=-16.735&longitude=-43.86167&current=temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code&timezone=auto`:

```json
{
  "latitude": -16.69596,
  "longitude": -43.83954,
  "generationtime_ms": 0.18548965454101562,
  "utc_offset_seconds": -10800,
  "timezone": "America/Sao_Paulo",
  "timezone_abbreviation": "GMT-3",
  "elevation": 690.0,
  "current_units": {
    "time": "iso8601",
    "interval": "seconds",
    "temperature_2m": "°C",
    "relative_humidity_2m": "%",
    "apparent_temperature": "°C",
    "wind_speed_10m": "km/h",
    "weather_code": "wmo code"
  },
  "current": {
    "time": "2026-11-07T11:45",
    "interval": 900,
    "temperature_2m": 32.0,
    "relative_humidity_2m": 48,
    "apparent_temperature": 36.9,
    "wind_speed_10m": 8.7,
    "weather_code": 1
  }
}
```

JSON da primeira chamada (geocoding), que fornece as coordenadas:

```json
{"results":[{"id":3456814,"name":"Montes Claros","latitude":-16.735,"longitude":-43.86167,"elevation":686.0,"feature_code":"PPL","country_code":"BR","admin1_id":3457153,"admin2_id":6321583,"timezone":"America/Sao_Paulo","population":437601,"country_id":3469034,"country":"Brasil","admin1":"Minas Gerais","admin2":"Montes Claros"}],"generationtime_ms":0.15437603}
```

### d. Exemplo real do bloco XML recebido pela aplicação SOAP

Requisição enviada (operação `CountryCurrency`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <CountryCurrency xmlns="http://www.oorsprong.org/websamples.countryinfo">
      <sCountryISOCode>BR</sCountryISOCode>
    </CountryCurrency>
  </soap:Body>
</soap:Envelope>
```

Resposta recebida:

```xml
<?xml version="1.0" encoding="utf-8" standalone="no"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <m:CountryCurrencyResponse xmlns:m="http://www.oorsprong.org/websamples.countryinfo">
      <m:CountryCurrencyResult>
        <m:sISOCode>BRL</m:sISOCode>
        <m:sName>Brazil Real</m:sName>
      </m:CountryCurrencyResult>
    </m:CountryCurrencyResponse>
  </soap:Body>
</soap:Envelope>
```

### Comparação REST × SOAP (observações da implementação)

| Aspecto | REST (Open-Meteo) | SOAP (CountryInfoService) |
|---|---|---|
| Formato | JSON, leve e direto | XML com envelope, `Body` e namespaces |
| Requisição | `GET` com parâmetros na URL | `POST` com corpo XML e cabeçalho `SOAPAction` |
| Contrato | Documentação em texto (sem contrato formal) | WSDL descreve operações, tipos e endpoint |
| Complexidade do código | Baixa (URL + parse do JSON) | Maior (montar envelope, tratar namespaces) |
| Verbosidade | Resposta de poucas linhas | Resposta várias vezes maior para o mesmo dado |
| Erros | Código HTTP (4xx/5xx) + JSON de erro | `SOAP Fault` (HTTP 500) |

**Dificuldades encontradas:**
- Muitos Web Services SOAP públicos antigos (ex.: conversores de moeda do WebServiceX) estão fora do ar; por isso foi escolhido o CountryInfoService, que continua disponível.
- No SOAP, o prefixo do namespace da resposta varia (`m:`), então os elementos são buscados pelo nome local (`getElementsByTagNameNS("*", ...)`).
- O serviço SOAP é servido em HTTP (sem TLS) e as respostas vêm em inglês (`Brazil`, `Brasilia`).
- A API REST precisa de duas chamadas encadeadas (geocoding → forecast), porque a previsão exige coordenadas, não o nome da cidade.
- Para não depender de bibliotecas, foi preciso escrever um parser JSON mínimo (em um projeto real usaria Jackson/Gson).
