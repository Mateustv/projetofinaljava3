package br.com.caixa.cobranca.consulta.integration;

import br.com.caixa.cobranca.consulta.controllers.PainelDemonstracaoController;
import br.com.caixa.cobranca.http.Correlacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "consulta.eventos.enabled=false")
@ActiveProfiles("demonstracao")
@AutoConfigureMockMvc
class PainelDemonstracaoTest {
    @Autowired
    MockMvc mvc;
    private HttpServer fonte;
    private String url;
    private volatile int statusFonte;
    private final CopyOnWriteArrayList<String> correlacoes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void iniciarFonte() throws Exception {
        fonte = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fonte.createContext("/", exchange -> {
            correlacoes.add(exchange.getRequestHeaders().getFirst(Correlacao.HEADER));
            exchange.getRequestBody().readAllBytes();
            byte[] json = "{\"status\":\"teste\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusFonte, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        fonte.start();
        url = "http://127.0.0.1:" + fonte.getAddress().getPort();
    }

    @AfterEach
    void encerrarFonte() {
        fonte.stop(0);
    }

    @Test
    void paginaERecursosLocaisSaoServidosNoPerfil() throws Exception {
        mvc.perform(get("/demonstracao")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("O caminho de um boleto")));
        mvc.perform(get("/demonstracao/painel.js")).andExpect(status().isOk());
        mvc.perform(get("/demonstracao/painel.css")).andExpect(status().isOk());
        mvc.perform(get("/demonstracao/rastros/{id}", UUID.randomUUID())).andExpect(status().isOk())
                .andExpect(jsonPath("$.rastros").isArray());
    }

    @Test
    void proxyPreserva202409ECorrelacaoSemRetry() throws Exception {
        var controller = new PainelDemonstracaoController(RestClient.builder(), url, url);
        UUID id = UUID.randomUUID();
        try (var escopo = Correlacao.abrir(id)) {
            for (int status : new int[]{202, 409, 400, 503}) {
                statusFonte = status;
                var resposta = controller.emitir(new ObjectMapper().readTree("{\"convenio\":\"123456\"}"));
                assertEquals(status, resposta.getStatusCode().value());
                assertEquals(id.toString(), resposta.getHeaders().getFirst(Correlacao.HEADER));
                assertEquals("{\"status\":\"teste\"}", new String(resposta.getBody(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(java.util.Collections.nCopies(4, id.toString()), correlacoes);
    }

    @Test
    void fonteIndisponivelNaoViraJanelaVaziaComSucesso() {
        var controller = new PainelDemonstracaoController(RestClient.builder(), url, url);
        UUID id = UUID.randomUUID();
        statusFonte = 404;
        var fluxo = controller.fluxo(id);
        assertFalse(((PainelDemonstracaoController.Fonte) fluxo.get("emissor")).disponivel());
        assertNotNull(((PainelDemonstracaoController.Fonte) fluxo.get("registro")).erro());
        assertFalse(((PainelDemonstracaoController.Fonte) fluxo.get("quarentena")).disponivel());
        assertEquals(3, correlacoes.size());
    }

    @Test
    void falhaDeTransporteDoEmissorRetorna503Explicito() throws Exception {
        var controller = new PainelDemonstracaoController(RestClient.builder(), url, url);
        fonte.stop(0);
        var resposta = controller.emitir(new ObjectMapper().readTree("{}"));
        assertEquals(503, resposta.getStatusCode().value());
        assertTrue(new String(resposta.getBody(), StandardCharsets.UTF_8)
                .contains("EMISSOR_INDISPONIVEL_NO_PAINEL"));
    }

    @Test
    void destinosDoProxyDevemSerLocaisEFixos() {
        for (String url : new String[]{"https://localhost:8081", "http://example.com",
                "http://localhost:8081/segredo", "http://user:pass@localhost:8081",
                "http://localhost:8081?url=externo"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PainelDemonstracaoController(RestClient.builder(), url, "http://localhost:8082"));
        }
    }
}
