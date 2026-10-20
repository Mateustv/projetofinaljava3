package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.broker.BrokerApplication;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.jms.config.JmsListenerEndpointRegistry;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.artemis.broker-url=vm://50",
        "consulta.eventos.enabled=true"
})
class ConsultaEventosTest {

    private static ActiveMQServer broker;
    private static ActiveMQConnectionFactory factory;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    JmsListenerEndpointRegistry listeners;
    @MockBean(name = "registroBoletoClient")
    BoletoFonteClient registro;
    @MockBean(name = "emissorBoletoClient")
    BoletoFonteClient emissor;
    @SpyBean
    ConsultaBoletoService consulta;
    @SpyBean
    ConsultaEventoListener listener;

    @BeforeAll
    static void iniciar(@TempDir Path dados) throws Exception {
        broker = BrokerApplication.criar(dados, "vm://50");
        broker.start();
        factory = new ActiveMQConnectionFactory("vm://50");
    }

    @AfterAll
    static void encerrar() throws Exception {
        factory.close();
        broker.stop();
    }

    @BeforeEach
    void limpar() throws Exception {
        listeners.stop();
        broker.locateQueue(new SimpleString(BrokerApplication.EVENTOS_CONSULTA)).deleteAllReferences();
        broker.locateQueue(new SimpleString(BrokerApplication.EVENTOS_QUARENTENA)).deleteAllReferences();
        cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).clear();
        clearInvocations(consulta, listener);
    }

    @AfterEach
    void pararListener() {
        listeners.stop();
    }

    @ParameterizedTest
    @EnumSource(value = TipoEventoBoleto.class, names = {"BoletoRegistrado", "RegistroRejeitado"})
    void eventoFinalInvalidaTransitorioAntesDoTtl(TipoEventoBoleto tipo) throws Exception {
        UUID id = UUID.randomUUID();
        var transitorio = boleto(id, TipoEventoBoleto.EmissaoAceita);
        var resultado = boleto(id, tipo);
        when(registro.buscar(id)).thenThrow(new BoletoNaoEncontradoException()).thenReturn(resultado);
        when(emissor.buscar(id)).thenReturn(transitorio);
        assertEquals(transitorio.status(), consulta.consultar(id).status());
        assertEquals(transitorio.status(), consulta.consultar(id).status());
        enviar(evento(id, tipo));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(resultado.status(), consulta.consultar(id).status());
        verify(registro, times(2)).buscar(id);
        verify(emissor, times(1)).buscar(id);
    }

    @Test
    void duplicataForaDeOrdemNaoRebaixaEstadoNemCriaProjecaoIncompleta() throws Exception {
        UUID id = UUID.randomUUID();
        var resultado = boleto(id, TipoEventoBoleto.BoletoRegistrado);
        when(registro.buscar(id)).thenReturn(resultado);
        var evento = evento(id, TipoEventoBoleto.BoletoRegistrado);
        enviar(evento);
        enviar(evento);
        enviar(evento(id, TipoEventoBoleto.EmissaoAceita));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(resultado, consulta.consultar(id));
        assertEquals(resultado, consulta.consultar(id));
        verify(consulta, times(2)).invalidar(id);
        verify(registro, times(1)).buscar(id);
        assertEquals(0, broker.locateQueue(new SimpleString(BrokerApplication.EVENTOS_QUARENTENA)).getMessageCount());
    }

    @Test
    void eventoDeOutroBoletoNaoExpulsaCacheValido() throws Exception {
        UUID id = UUID.randomUUID();
        var resultado = boleto(id, TipoEventoBoleto.BoletoRegistrado);
        when(registro.buscar(id)).thenReturn(resultado);
        consulta.consultar(id);
        enviar(evento(UUID.randomUUID(), TipoEventoBoleto.BoletoRegistrado));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(resultado, consulta.consultar(id));
        verify(registro, times(1)).buscar(id);
    }

    @Test
    void missEmAndamentoNaoReinstalaTransitorioDepoisDoAckDoEvento() throws Exception {
        UUID id = UUID.randomUUID();
        var transitorio = boleto(id, TipoEventoBoleto.EmissaoAceita);
        var resultado = boleto(id, TipoEventoBoleto.BoletoRegistrado);
        var leituraEmVoo = new CountDownLatch(1);
        var liberarLeitura = new CountDownLatch(1);
        var invalidacaoIniciada = new CountDownLatch(1);
        when(registro.buscar(id)).thenAnswer(invocation -> {
            leituraEmVoo.countDown();
            assertTrue(liberarLeitura.await(10, TimeUnit.SECONDS));
            return transitorio;
        }).thenReturn(resultado);
        doAnswer(invocation -> {
            invalidacaoIniciada.countDown();
            return invocation.callRealMethod();
        }).when(consulta).invalidar(id);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var leitura = executor.submit(() -> consulta.consultar(id));
            assertTrue(leituraEmVoo.await(5, TimeUnit.SECONDS));
            enviar(evento(id, TipoEventoBoleto.BoletoRegistrado));
            listeners.start();
            assertTrue(invalidacaoIniciada.await(5, TimeUnit.SECONDS));
            assertEquals(1, broker.locateQueue(new SimpleString(BrokerApplication.EVENTOS_CONSULTA)).getDeliveringCount());
            liberarLeitura.countDown();
            assertEquals(transitorio.status(), leitura.get(5, TimeUnit.SECONDS).status());
            aguardarFilaVazia();
            assertNull(cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).get(id));
            assertEquals(resultado, consulta.consultar(id));
            verify(registro, times(2)).buscar(id);
        } finally {
            liberarLeitura.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaDepoisDeInvalidarAntesDoAckRepeteSemEfeitoIncorreto() throws Exception {
        UUID id = UUID.randomUUID();
        when(registro.buscar(id)).thenReturn(boleto(id, TipoEventoBoleto.BoletoRegistrado));
        consulta.consultar(id);
        var entregas = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (entregas.incrementAndGet() == 1) {
                throw new jakarta.jms.JMSException("Falha antes do ack.");
            }
            return null;
        }).when(listener).receber(any(), any());
        enviar(evento(id, TipoEventoBoleto.BoletoRegistrado));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(2, entregas.get());
        assertNull(cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).get(id));
        assertEquals(TipoEventoBoleto.BoletoRegistrado.status(), consulta.consultar(id).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalido", "null", "{}", "{\"tipo\":\"TipoDesconhecido\"}"})
    void eventoInvalidoVaiParaQuarentenaSemAfetarBom(String invalido) throws Exception {
        UUID id = UUID.randomUUID();
        enviarJson(invalido);
        enviar(evento(id, TipoEventoBoleto.BoletoRegistrado));
        listeners.start();
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.EVENTOS_QUARENTENA))) {
            connection.start();
            var message = (TextMessage) dlq.receive(5000);
            assertNotNull(message);
            assertEquals(invalido, message.getText());
            assertEquals("EVENTO_INVALIDO", message.getStringProperty("tipo_erro"));
            session.commit();
        }
        aguardarFilaVazia();
        verify(consulta, times(1)).invalidar(id);
        verifyNoInteractions(registro, emissor);
    }

    @Test
    void corpoNaoTextualEEnvelopeInconsistenteSaoIsoladosSemPerderOriginal() throws Exception {
        var evento = evento(UUID.randomUUID(), TipoEventoBoleto.BoletoRegistrado);
        var json = mapper.valueToTree(evento);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("status", "AGUARDANDO_REGISTRO");
        enviarJson(mapper.writeValueAsString(json));
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createTopic(BrokerApplication.EVENTOS))) {
            var bytes = session.createBytesMessage();
            bytes.writeUTF("corpo original");
            bytes.setJMSCorrelationID(evento.correlationId().toString());
            producer.send(bytes);
            session.commit();
        }
        listeners.start();
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.EVENTOS_QUARENTENA))) {
            connection.start();
            var inconsistente = (TextMessage) dlq.receive(5000);
            assertNotNull(inconsistente);
            assertEquals(json, mapper.readTree(inconsistente.getText()));
            assertEquals("EVENTO_INVALIDO", inconsistente.getStringProperty("tipo_erro"));
            var bytes = (jakarta.jms.BytesMessage) dlq.receive(5000);
            assertNotNull(bytes);
            assertEquals("corpo original", bytes.readUTF());
            assertEquals(evento.correlationId().toString(), bytes.getJMSCorrelationID());
            assertEquals("TIPO_MENSAGEM_INVALIDO", bytes.getStringProperty("tipo_erro"));
            session.commit();
        }
        aguardarFilaVazia();
        verify(consulta, never()).invalidar(any());
    }

    @Test
    void falhaTemporariaDeInvalidacaoNaoConfirmaEsgotaEmQuarentenaENaoBloqueiaBom() throws Exception {
        UUID ruim = UUID.randomUUID();
        UUID bom = UUID.randomUUID();
        doThrow(new IllegalStateException("Cache indisponivel.")).when(consulta).invalidar(ruim);
        enviar(evento(ruim, TipoEventoBoleto.BoletoRegistrado));
        enviar(evento(bom, TipoEventoBoleto.RegistroRejeitado));
        listeners.start();
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> verify(consulta).invalidar(bom));
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.EVENTOS_QUARENTENA))) {
            connection.start();
            var message = (TextMessage) dlq.receive(10000);
            assertNotNull(message);
            assertEquals(ruim, mapper.readValue(message.getText(), EventoBoleto.class).identificadorBoleto());
            assertEquals(BrokerApplication.EVENTOS_CONSULTA, message.getStringProperty("_AMQ_ORIG_QUEUE"));
            session.commit();
        }
        aguardarFilaVazia();
        verify(consulta, times(3)).invalidar(ruim);
    }

    private BoletoConsulta boleto(UUID id, TipoEventoBoleto tipo) {
        return new BoletoConsulta(id, tipo.status(), LocalDateTime.of(2026, 10, 20, 15, 0),
                tipo == TipoEventoBoleto.BoletoRegistrado ? "NAO-PAGAVEL-SIM-LINHA-" + id : null,
                tipo == TipoEventoBoleto.BoletoRegistrado ? "NAO-PAGAVEL-SIM-BARRAS-" + id : null,
                tipo == TipoEventoBoleto.BoletoRegistrado ? "SIM-" + id : null,
                tipo == TipoEventoBoleto.BoletoRegistrado ? LocalDateTime.of(2026, 10, 20, 15, 1) : null,
                tipo == TipoEventoBoleto.RegistroRejeitado ? "SIM-REJ-001" : null,
                tipo == TipoEventoBoleto.RegistroRejeitado ? "Rejeicao simulada." : null);
    }

    private EventoBoleto evento(UUID id, TipoEventoBoleto tipo) {
        return new EventoBoleto(UUID.randomUUID(), tipo, id, "123456", tipo.status(), LocalDateTime.now(), id);
    }

    private void enviar(EventoBoleto evento) throws Exception {
        enviarJson(mapper.writeValueAsString(evento));
    }

    private void enviarJson(String json) throws Exception {
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createTopic(BrokerApplication.EVENTOS))) {
            producer.send(session.createTextMessage(json));
            session.commit();
        }
    }

    private void aguardarFilaVazia() {
        await().atMost(12, TimeUnit.SECONDS).untilAsserted(() -> {
            var fila = broker.locateQueue(new SimpleString(BrokerApplication.EVENTOS_CONSULTA));
            assertEquals(0, fila.getMessageCount());
            assertEquals(0, fila.getDeliveringCount());
        });
    }
}
