package br.com.caixa.cobranca.consulta.controllers;

import br.com.caixa.cobranca.consulta.configs.ConsultaClientConfiguration;
import br.com.caixa.cobranca.http.Correlacao;
import br.com.caixa.cobranca.http.demo.Rastros;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@Profile("demonstracao")
public class PainelDemonstracaoController {
    private final RestClient http;
    private final String emissor;
    private final String registro;

    public PainelDemonstracaoController(RestClient.Builder builder,
            @Value("${integracoes.emissor.base-url}") String emissor,
            @Value("${integracoes.registro.base-url}") String registro) {
        this.emissor = enderecoLocal(emissor);
        this.registro = enderecoLocal(registro);
        this.http = ConsultaClientConfiguration.configurar(builder, 1000, 2000).build();
    }

    private static String enderecoLocal(String valor) {
        URI uri = URI.create(valor);
        if (!"http".equals(uri.getScheme()) || uri.getUserInfo() != null
                || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Painel exige URLs HTTP locais, sem caminho ou credenciais.");
        }
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    @GetMapping(value = {"/demonstracao", "/demonstracao/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ClassPathResource pagina() {
        return new ClassPathResource("demonstracao/index.html");
    }

    @GetMapping(value = "/demonstracao/painel.js", produces = "text/javascript")
    public ClassPathResource script() {
        return new ClassPathResource("demonstracao/painel.js");
    }

    @GetMapping(value = "/demonstracao/painel.css", produces = "text/css")
    public ClassPathResource estilos() {
        return new ClassPathResource("demonstracao/painel.css");
    }

    @PostMapping("/demonstracao/api/emissoes")
    public ResponseEntity<byte[]> emitir(@RequestBody JsonNode pedido) {
        UUID correlacao = Correlacao.atualOu(UUID.randomUUID());
        try {
            return http.method(HttpMethod.POST).uri(emissor + "/boletos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(Correlacao.HEADER, correlacao.toString()).body(pedido)
                    .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(Correlacao.HEADER, correlacao.toString())
                            .body(response.getBody().readAllBytes()));
        } catch (RestClientException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Emissor indisponivel no painel. correlation_id={}, tipo={}",
                    correlacao, ex.getClass().getSimpleName());
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"codigo\":\"EMISSOR_INDISPONIVEL_NO_PAINEL\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/demonstracao/api/fluxos/{boleto}")
    public Map<String, Object> fluxo(@PathVariable UUID boleto) {
        var fontes = new LinkedHashMap<String, Object>();
        fontes.put("emissor", observar(emissor + "/demonstracao/rastros/" + boleto));
        fontes.put("registro", observar(registro + "/demonstracao/rastros/" + boleto));
        fontes.put("consulta", new RastrosControllerLocal(Rastros.buscar(boleto)));
        fontes.put("quarentena", observar(registro + "/demonstracao/quarentena/" + boleto));
        return fontes;
    }

    private Fonte observar(String url) {
        try {
            JsonNode body = http.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new RestClientException("Resposta de diagnostico vazia.");
            }
            return new Fonte(true, body, null);
        } catch (RestClientException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Fonte de diagnostico indisponivel. url={}, tipo={}", url, ex.getClass().getSimpleName());
            return new Fonte(false, null, "Fonte indisponivel ou sem perfil demonstracao: "
                    + ex.getClass().getSimpleName());
        }
    }

    public record Fonte(boolean disponivel, JsonNode dados, String erro) {
    }

    public record RastrosControllerLocal(java.util.List<Rastros.Rastro> rastros) {
    }
}
