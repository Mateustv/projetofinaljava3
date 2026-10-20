package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.broker.BrokerApplication;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.eventos.EventoOutboxRepository;
import br.com.caixa.cobranca.eventos.EventoOutboxDispatcher;
import br.com.caixa.cobranca.http.Correlacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registro-jms;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.artemis.broker-url=vm://43",
        "registro.mensageria.enabled=true"
})
class RegistroEntregaJmsTest {

    private static ActiveMQServer server;
    private static ActiveMQConnectionFactory factory;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JmsListenerEndpointRegistry listeners;
    @Autowired
    OperacaoRegistradoraSimuladaRepository operacoes;
    @Autowired
    RegistradoraSimuladaPersistence simulacao;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EventoOutboxRepository eventos;
    @SpyBean
    RegistroBoletoRepository repository;
    @SpyBean
    RegistradoraSimulada registradora;
    @SpyBean
    RegistroCommandListener listener;

    @BeforeAll
    static void iniciarBroker(@TempDir Path dados) throws Exception {
        server = BrokerApplication.criar(dados, "vm://43");
        server.start();
        factory = new ActiveMQConnectionFactory("vm://43");
    }

    @AfterAll
    static void pararBroker() throws Exception {
        factory.close();
        server.stop();
    }

    @BeforeEach
    void limpar() throws Exception {
        listeners.stop();
        server.locateQueue(new SimpleString(BrokerApplication.COMANDOS)).deleteAllReferences();
        server.locateQueue(new SimpleString(BrokerApplication.QUARENTENA)).deleteAllReferences();
        server.locateQueue(new SimpleString(BrokerApplication.EVENTOS_CONSULTA)).deleteAllReferences();
        eventos.deleteAll();
        repository.deleteAll();
        operacoes.deleteAll();
        clearInvocations(repository, registradora, listener);
    }

    @AfterEach
    void pararConsumidor() {
        listeners.stop();
    }

    @Test
    void correlacaoAtravessaComandoJmsResultadoIntencaoEEventoJms() throws Exception {
        var base = comando();
        UUID correlation = UUID.randomUUID();
        var comando = new RegistrarBoletoCommand(base.identificador(), base.aceite(), base.solicitacao(), correlation);
        doAnswer(invocation -> {
            assertEquals(correlation, Correlacao.atualOu(null));
            return invocation.callRealMethod();
        }).when(registradora).registrar(comando);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertNull(Correlacao.atualOu(null));
            return null;
        }).when(listener).receber(any(), any());
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS))) {
            var message = session.createTextMessage(mapper.writeValueAsString(comando));
            message.setJMSCorrelationID(correlation.toString());
            producer.send(message);
            session.commit();
        }
        listeners.start();
        aguardarFilaVazia();
        var intencao = eventos.findAll().get(0);
        assertEquals(correlation, mapper.readValue(intencao.getEventoJson(), EventoBoleto.class).correlationId());
        var jms = new JmsTemplate(factory);
        jms.setPubSubDomain(true);
        jms.setSessionTransacted(true);
        jms.setExplicitQosEnabled(true);
        jms.setDeliveryPersistent(true);
        new EventoOutboxDispatcher(eventos, mapper, jms, Clock.systemDefaultZone()).publicarPendentes();
        assertNotNull(eventos.findById(intencao.getIdEvento()).orElseThrow().getPublicadoEm());
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(
                     BrokerApplication.EVENTOS + "::" + BrokerApplication.EVENTOS_CONSULTA))) {
            connection.start();
            var message = (TextMessage) consumer.receive(3000);
            assertNotNull(message);
            assertEquals(correlation.toString(), message.getJMSCorrelationID());
            assertEquals(correlation, mapper.readValue(message.getText(), EventoBoleto.class).correlationId());
            session.commit();
        }
    }

    @Test
    void correlacaoJmsDivergenteDoEnvelopeVaiParaQuarentena() throws Exception {
        var comando = comando();
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS));
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
            var message = session.createTextMessage(mapper.writeValueAsString(comando));
            message.setJMSCorrelationID(UUID.randomUUID().toString());
            producer.send(message);
            session.commit();
            listeners.start();
            connection.start();
            var isolado = (TextMessage) dlq.receive(5000);
            assertNotNull(isolado);
            assertEquals(message.getText(), isolado.getText());
            assertEquals("ComandoRegistroInvalidoException", isolado.getStringProperty("tipo_erro"));
            session.commit();
        }
        aguardarFilaVazia();
        assertEquals(0, repository.count());
        assertEquals(0, eventos.count());
        assertEquals(0, operacoes.count());
    }

    @Test
    void consomeComIdempotenciaHistoricoEResultadoUnicos() throws Exception {
        var comando = comando();
        enviar(mapper.writeValueAsString(comando));
        enviar(mapper.writeValueAsString(comando));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(StatusBoleto.REGISTRADO, repository.findById(comando.identificador()).orElseThrow().comoConsulta().status());
        assertEquals(1, repository.count());
        assertEquals(1, operacoes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        verify(registradora, times(1)).registrar(comando);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{JSON_INVALIDO", "null", "{}"})
    void invalidoVaiUmaVezParaQuarentenaSemBloquearValido(String json) throws Exception {
        var valido = comando();
        enviar(json);
        enviar(mapper.writeValueAsString(valido));
        listeners.start();
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
            connection.start();
            var message = (TextMessage) dlq.receive(5000);
            assertNotNull(message);
            assertEquals(json, message.getText());
            assertNotNull(message.getStringProperty("tipo_erro"));
            assertEquals(1, message.getIntProperty("entregas"));
            session.commit();
        }
        aguardarFilaVazia();
        assertTrue(repository.existsById(valido.identificador()));
        assertEquals(1, operacoes.count());
        verify(registradora, times(1)).registrar(any());
    }

    @Test
    void corpoNaoTextualTambemEPreservadoNaQuarentena() throws Exception {
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS));
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
            var bytes = session.createBytesMessage();
            bytes.writeUTF("corpo-invalido-preservado");
            producer.send(bytes);
            session.commit();
            connection.start();
            listeners.start();
            var message = (jakarta.jms.BytesMessage) dlq.receive(5000);
            assertNotNull(message);
            assertEquals("corpo-invalido-preservado", message.readUTF());
            assertEquals("TIPO_MENSAGEM_INVALIDO", message.getStringProperty("tipo_erro"));
            assertEquals(1, message.getIntProperty("entregas"));
            session.commit();
        }
        assertEquals(0, repository.count());
        assertEquals(0, operacoes.count());
    }

    @Test
    void conflitoVaiParaQuarentenaSemSobrescreverOriginal() throws Exception {
        var original = comando();
        var conflitante = new RegistrarBoletoCommand(original.identificador(), original.aceite(),
                RegistroComandoControllerTest.comando(original.identificador(), "151.00").solicitacao());
        enviar(mapper.writeValueAsString(original));
        enviar(mapper.writeValueAsString(conflitante));
        listeners.start();
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
            connection.start();
            var message = (TextMessage) dlq.receive(5000);
            assertNotNull(message);
            assertEquals("ComandoRegistroConflitanteException", message.getStringProperty("tipo_erro"));
            session.commit();
        }
        assertEquals(1, repository.count());
        assertEquals(1, operacoes.count());
        verify(registradora, times(1)).registrar(original);
    }

    @Test
    void falhaTemporariaEsgotaTresTentativasENaoImpedeComandoSeguinte() throws Exception {
        var indisponivel = comando();
        var valido = comando();
        doThrow(new FalhaAoRegistrarBoletoException(new IllegalStateException("Indisponivel.")))
                .when(registradora).registrar(indisponivel);
        enviar(mapper.writeValueAsString(indisponivel));
        enviar(mapper.writeValueAsString(valido));
        listeners.start();
        await().atMost(3, SECONDS).until(() -> repository.existsById(valido.identificador()));
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
            connection.start();
            var message = (TextMessage) dlq.receive(10000);
            assertNotNull(message);
            assertEquals(indisponivel, mapper.readValue(message.getText(), RegistrarBoletoCommand.class));
            assertEquals(BrokerApplication.COMANDOS, message.getStringProperty("_AMQ_ORIG_ADDRESS"));
            session.commit();
        }
        aguardarFilaVazia();
        verify(registradora, times(3)).registrar(indisponivel);
        assertFalse(repository.existsById(indisponivel.identificador()));
        assertEquals(1, operacoes.count());
    }

    @Test
    void falhaTemporariaRecuperaNaSegundaEntrega() throws Exception {
        var comando = comando();
        doThrow(new FalhaAoRegistrarBoletoException(new IllegalStateException("Temporaria.")))
                .doCallRealMethod().when(registradora).registrar(comando);
        enviar(mapper.writeValueAsString(comando));
        listeners.start();
        aguardarFilaVazia();
        verify(registradora, times(2)).registrar(comando);
        assertEquals(1, operacoes.count());
        assertEquals(1, repository.count());
        assertEquals(0, server.locateQueue(new SimpleString(BrokerApplication.QUARENTENA)).getMessageCount());
    }

    @Test
    void rejeicaoDeNegocioEFinalENaoERetryNemDlq() throws Exception {
        var comando = comando();
        doAnswer(invocation -> simulacao.registrar(comando, RegistradoraSimulada.Cenario.REJEITAR))
                .when(registradora).registrar(comando);
        enviar(mapper.writeValueAsString(comando));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(StatusBoleto.REJEITADO, repository.findById(comando.identificador()).orElseThrow().comoConsulta().status());
        verify(registradora, times(1)).registrar(comando);
        assertEquals(0, server.locateQueue(new SimpleString(BrokerApplication.QUARENTENA)).getMessageCount());
    }

    @Test
    void falhaDepoisDoCommitJpaAntesDoAckJmsNaoRepeteEfeitoExterno() throws Exception {
        var comando = comando();
        var entregas = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (entregas.incrementAndGet() == 1) {
                assertTrue(repository.existsById(comando.identificador()));
                throw new JMSException("Falha simulada antes do ack JMS.");
            }
            return null;
        }).when(listener).receber(any(), any());
        enviar(mapper.writeValueAsString(comando));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(2, entregas.get());
        verify(registradora, times(1)).registrar(comando);
        assertEquals(1, repository.count());
        assertEquals(1, operacoes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
    }

    @Test
    void rollbackJpaDepoisDeFlushRecuperaPeloLedgerNaReentrega() throws Exception {
        var comando = comando();
        var gravacoes = new AtomicInteger();
        doAnswer(invocation -> {
            RegistroBoletoEntity entidade = invocation.getArgument(0);
            var resultado = repository.save(entidade);
            repository.flush();
            if (gravacoes.incrementAndGet() == 1) {
                throw new DataAccessResourceFailureException("Falha local depois do flush.");
            }
            return resultado;
        }).when(repository).saveAndFlush(any());
        enviar(mapper.writeValueAsString(comando));
        listeners.start();
        aguardarFilaVazia();
        assertEquals(2, gravacoes.get());
        assertEquals(1, repository.count());
        assertEquals(1, operacoes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falhaNaTransferenciaParaQuarentenaReverteAckDoOriginal(boolean enderecoSemFila) throws Exception {
        enviar("{invalido");
        if (enderecoSemFila) {
            server.destroyQueue(new SimpleString(BrokerApplication.QUARENTENA), null, false, true, false);
        }
        var destinoErrado = new RegistroCommandListener(mapper, null,
                enderecoSemFila ? BrokerApplication.QUARENTENA : "quarentena.inexistente");
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var original = consumer.receive(3000);
            assertNotNull(original);
            assertThrows(JMSException.class, () -> destinoErrado.receber(original, session));
            session.rollback();
            var reentregue = consumer.receive(4000);
            assertNotNull(reentregue);
            assertEquals(2, reentregue.getIntProperty("JMSXDeliveryCount"));
            if (enderecoSemFila) {
                server.createQueue(new QueueConfiguration(BrokerApplication.QUARENTENA)
                        .setAddress(BrokerApplication.QUARENTENA).setRoutingType(RoutingType.ANYCAST).setDurable(true));
            }
            listener.receber(reentregue, session);
            session.commit();
            try (var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA))) {
                assertNotNull(dlq.receive(3000));
                session.commit();
            }
            assertNull(consumer.receive(200));
        }
    }

    private RegistrarBoletoCommand comando() {
        return RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
    }

    private void enviar(String json) throws Exception {
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS))) {
            producer.send(session.createTextMessage(json));
            session.commit();
        }
    }

    private void aguardarFilaVazia() {
        await().atMost(12, SECONDS).untilAsserted(() -> {
            var fila = server.locateQueue(new SimpleString(BrokerApplication.COMANDOS));
            assertEquals(0, fila.getMessageCount());
            assertEquals(0, fila.getDeliveringCount());
        });
    }
}
