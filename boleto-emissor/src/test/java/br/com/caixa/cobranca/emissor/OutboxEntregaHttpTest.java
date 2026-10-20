package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "emissao.outbox.dispatch.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:outbox-e5;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class OutboxEntregaHttpTest {

    @Autowired
    private ComandoRegistroOutboxRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    private final Instant inicio = Instant.parse("2026-10-20T12:00:00Z");
    private final LocalDateTime agora = LocalDateTime.ofInstant(inicio, ZoneOffset.UTC);
    private final AtomicInteger respostas = new AtomicInteger(503);
    private final AtomicInteger chamadas = new AtomicInteger();
    private HttpServer servidor;
    private RegistroCommandPublisher publisher;

    @BeforeEach
    void iniciarTransporte() throws Exception {
        repository.deleteAll();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/interno/comandos/registro", exchange -> {
            exchange.getRequestBody().readAllBytes();
            chamadas.incrementAndGet();
            exchange.sendResponseHeaders(respostas.get(), -1);
            exchange.close();
        });
        servidor.start();
        publisher = new HttpRegistroCommandPublisher(RestClient.builder(),
                "http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void pararTransporte() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void backoffPersistidoImpedeEnvioAntecipadoERecuperaNaSegundaTentativa() throws Exception {
        UUID id = criarComando(agora);

        despachar(0);
        assertEquals(agora.plusSeconds(1), carregar(id).getProximaTentativaEm());
        despachar(0);
        assertEquals(1, chamadas.get());
        respostas.set(200);
        despachar(1);

        var entregue = carregar(id);
        assertEquals(2, chamadas.get());
        assertEquals(2, entregue.getTentativas());
        assertEquals(agora.plusSeconds(1), entregue.getEntregueEm());
        assertNull(entregue.getComandoJson());
        assertNull(entregue.getUltimoErro());
        despachar(10);
        assertEquals(2, chamadas.get());
    }

    @Test
    void tresFalhasHttp503IsolamComandoESuspendemEnviosPosteriores() throws Exception {
        UUID id = criarComando(agora);
        despachar(0);
        despachar(1);
        assertEquals(agora.plusSeconds(6), carregar(id).getProximaTentativaEm());
        despachar(5);
        assertEquals(2, chamadas.get());
        despachar(6);
        despachar(60);

        var isolado = carregar(id);
        assertEquals(3, chamadas.get());
        assertEquals(3, isolado.getTentativas());
        assertNotNull(isolado.getComandoJson());
        assertNotNull(isolado.getFalhaDefinitivaEm());
        assertNull(isolado.getEntregueEm());
        assertEquals("HTTP_503", isolado.getUltimoErro());
        assertTrue(repository.buscarElegiveis(agora.plusDays(1), PageRequest.of(0, 20)).isEmpty());
    }

    @Test
    void loteDeVinteMensagensEnvenenadasNaoImpedeEntregaDaProximaMensagemBoa() throws Exception {
        for (int i = 0; i < 20; i++) {
            repository.saveAndFlush(new ComandoRegistroOutboxEntity(UUID.randomUUID(), "{invalido", agora));
        }
        UUID boa = criarComando(agora.plusSeconds(1));
        respostas.set(202);

        despachar(1);
        assertEquals(0, chamadas.get());
        despachar(2);

        assertEquals(1, chamadas.get());
        assertNotNull(carregar(boa).getEntregueEm());
        assertEquals(20, repository.findAll().stream().filter(c -> c.getFalhaDefinitivaEm() != null).count());
    }

    @Test
    void erroHttp400IsolaImediatamenteEPreservaPayloadSemReenviar() throws Exception {
        UUID id = criarComando(agora);
        respostas.set(400);

        despachar(0);
        despachar(10);

        assertEquals(1, chamadas.get());
        assertEquals(1, carregar(id).getTentativas());
        assertEquals("HTTP_400", carregar(id).getUltimoErro());
        assertNotNull(carregar(id).getFalhaDefinitivaEm());
        assertNotNull(carregar(id).getComandoJson());

        mvc.perform(get("/interno/outbox/falhas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identificador").value(id.toString()))
                .andExpect(jsonPath("$[0].tentativas").value(1))
                .andExpect(jsonPath("$[0].tipo_erro").value("HTTP_400"))
                .andExpect(jsonPath("$[0].falha_em").exists())
                .andExpect(jsonPath("$[0].comando_json").doesNotExist())
                .andExpect(jsonPath("$[0].solicitacao").doesNotExist());
    }

    @Test
    void redirecionamentoNaoEConfundidoComConfirmacaoDeEntrega() throws Exception {
        UUID id = criarComando(agora);
        respostas.set(302);

        despachar(0);

        assertNull(carregar(id).getEntregueEm());
        assertEquals(agora.plusSeconds(1), carregar(id).getProximaTentativaEm());
        assertNotNull(carregar(id).getComandoJson());
    }

    private void despachar(long segundos) {
        new OutboxDispatcher(repository, publisher, objectMapper,
                Clock.fixed(inicio.plusSeconds(segundos), ZoneOffset.UTC)).despacharPendentes();
    }

    private UUID criarComando(LocalDateTime criadoEm) throws Exception {
        UUID id = UUID.randomUUID();
        var comando = new RegistrarBoletoCommand(id,
                new BoletoAceito(id, StatusBoleto.AGUARDANDO_REGISTRO, criadoEm), null);
        repository.saveAndFlush(new ComandoRegistroOutboxEntity(
                id, objectMapper.writeValueAsString(comando), criadoEm));
        return id;
    }

    private ComandoRegistroOutboxEntity carregar(UUID id) {
        return repository.findById(id).orElseThrow();
    }
}
