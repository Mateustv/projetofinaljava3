package br.com.caixa.cobranca.eventos;

import br.com.caixa.cobranca.broker.BrokerApplication;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = EventoOutboxTest.Application.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos-outbox;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "eventos.outbox.enabled=false"
})
class EventoOutboxTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(EventosOutboxConfiguration.class)
    static class Application {
    }

    @TempDir
    Path dados;
    @Autowired
    EventoOutboxService service;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    PlatformTransactionManager transactionManager;
    @SpyBean
    EventoOutboxRepository repository;
    private ActiveMQServer broker;
    private ActiveMQConnectionFactory factory;
    private final Instant inicio = Instant.parse("2026-10-20T15:00:00Z");

    @BeforeEach
    void iniciar() throws Exception {
        repository.deleteAll();
        broker = BrokerApplication.criar(dados, "vm://49");
        broker.start();
        factory = new ActiveMQConnectionFactory("vm://49");
    }

    @AfterEach
    void parar() throws Exception {
        factory.close();
        broker.stop();
    }

    @ParameterizedTest
    @EnumSource(TipoEventoBoleto.class)
    void envelopeMinimoRoundtripSemDadosPessoais(TipoEventoBoleto tipo) throws Exception {
        var evento = evento(tipo);
        var json = mapper.valueToTree(evento);
        Set<String> campos = new HashSet<>();
        json.fieldNames().forEachRemaining(campos::add);
        assertEquals(Set.of("id_evento", "tipo", "identificador_boleto", "convenio",
                "status", "data_hora", "correlation_id"), campos);
        assertEquals(tipo.name(), json.get("tipo").asText());
        assertEquals(evento, mapper.readValue(mapper.writeValueAsString(evento), EventoBoleto.class));
        assertThrows(IllegalArgumentException.class, () -> new EventoBoleto(
                evento.idEvento(), tipo, evento.identificadorBoleto(), "123456",
                tipo.status() == br.com.caixa.cobranca.contracts.StatusBoleto.REGISTRADO
                        ? br.com.caixa.cobranca.contracts.StatusBoleto.REJEITADO
                        : br.com.caixa.cobranca.contracts.StatusBoleto.REGISTRADO,
                evento.dataHora(), evento.correlationId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id_evento", "tipo", "identificador_boleto", "convenio",
            "status", "data_hora", "correlation_id"})
    void campoObrigatorioNuloRecusaEnvelope(String campo) {
        var json = mapper.valueToTree(evento(TipoEventoBoleto.EmissaoAceita));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).putNull(campo);
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.treeToValue(json, EventoBoleto.class));
    }

    @Test
    void exigeTransacaoDoDonoENaoCriaIntencaoSeElaReverte() {
        var evento = evento(TipoEventoBoleto.EmissaoAceita);
        assertThrows(IllegalTransactionStateException.class, () -> service.adicionar(evento));
        assertThrows(IllegalStateException.class, () -> transacao().executeWithoutResult(tx -> {
            service.adicionar(evento);
            throw new IllegalStateException("Falha depois de gravar intencao.");
        }));
        assertEquals(0, repository.count());
    }

    @Test
    void commitDeNegocioComBrokerOfflinePreservaIntencaoERecuperaPublicacao() throws Exception {
        var evento = evento(TipoEventoBoleto.BoletoRegistrado);
        factory.close();
        broker.stop();
        transacao().executeWithoutResult(tx -> service.adicionar(evento));
        try (var offline = new ActiveMQConnectionFactory(
                "tcp://127.0.0.1:1?callTimeout=300&initialConnectAttempts=0&reconnectAttempts=0")) {
            despachar(0, offline);
        }
        var falha = repository.findById(evento.idEvento()).orElseThrow();
        assertNull(falha.getPublicadoEm());
        assertEquals(1, falha.getTentativas());
        assertEquals(evento, mapper.readValue(falha.getEventoJson(), EventoBoleto.class));
        assertEquals(agora(1), falha.getProximaTentativaEm());

        broker = BrokerApplication.criar(dados, "vm://49");
        broker.start();
        factory = new ActiveMQConnectionFactory("vm://49");
        despachar(0, factory);
        assertNull(repository.findById(evento.idEvento()).orElseThrow().getPublicadoEm());
        despachar(1, factory);
        assertEquals(agora(1), repository.findById(evento.idEvento()).orElseThrow().getPublicadoEm());
        assertEquals(2, repository.findById(evento.idEvento()).orElseThrow().getTentativas());
        receber(evento, 1);
    }

    @Test
    void esgotamentoIsolaSemBloquearEventoNovoNemApagarPayload() throws Exception {
        var isolado = evento(TipoEventoBoleto.EmissaoAceita);
        transacao().executeWithoutResult(tx -> service.adicionar(isolado));
        try (var offline = new ActiveMQConnectionFactory(
                "tcp://127.0.0.1:1?callTimeout=300&initialConnectAttempts=0&reconnectAttempts=0")) {
            despachar(0, offline);
            despachar(1, offline);
            despachar(5, offline);
            assertEquals(2, repository.findById(isolado.idEvento()).orElseThrow().getTentativas());
            despachar(6, offline);
        }
        var falha = repository.findById(isolado.idEvento()).orElseThrow();
        assertEquals(3, falha.getTentativas());
        assertNotNull(falha.getFalhaDefinitivaEm());
        assertNotNull(falha.getEventoJson());
        var novo = evento(TipoEventoBoleto.RegistroRejeitado);
        transacao().executeWithoutResult(tx -> service.adicionar(novo));
        despachar(20, factory);
        receber(novo, 1);
        assertEquals(3, repository.findById(isolado.idEvento()).orElseThrow().getTentativas());
    }

    @Test
    void falhaLocalDepoisDeCommitJmsPodeDuplicarMesmoIdEvento() throws Exception {
        var evento = evento(TipoEventoBoleto.BoletoRegistrado);
        transacao().executeWithoutResult(tx -> service.adicionar(evento));
        doThrow(new DataAccessResourceFailureException("Falha local depois do commit JMS."))
                .when(repository).save(any(EventoOutboxEntity.class));
        assertThrows(DataAccessResourceFailureException.class, () -> despachar(0, factory));
        assertNull(repository.findById(evento.idEvento()).orElseThrow().getPublicadoEm());
        reset(repository);
        despachar(1, factory);
        receber(evento, 2);
        assertEquals(evento.idEvento(), repository.findAll().get(0).getIdEvento());
    }

    @Test
    void destinoSemAssinaturaNaoConcluiOutbox() {
        var evento = evento(TipoEventoBoleto.EmissaoAceita);
        transacao().executeWithoutResult(tx -> service.adicionar(evento));
        assertDoesNotThrow(() -> broker.destroyQueue(
                new org.apache.activemq.artemis.api.core.SimpleString(BrokerApplication.EVENTOS_CONSULTA),
                null, false, true, false));
        despachar(0, factory);
        assertNull(repository.findById(evento.idEvento()).orElseThrow().getPublicadoEm());
        assertEquals(1, repository.findById(evento.idEvento()).orElseThrow().getTentativas());
    }

    @Test
    void assinanteOfflineNaoBloqueiaOutroEPreservaEventoAposReinicio() throws Exception {
        broker.createQueue(new QueueConfiguration("boletos.eventos.bi")
                .setAddress(BrokerApplication.EVENTOS).setRoutingType(RoutingType.MULTICAST).setDurable(true));
        var evento = evento(TipoEventoBoleto.RegistroRejeitado);
        transacao().executeWithoutResult(tx -> service.adicionar(evento));
        despachar(0, factory);
        receber(evento, 1);
        factory.close();
        broker.stop();
        broker = BrokerApplication.criar(dados, "vm://49");
        broker.start();
        factory = new ActiveMQConnectionFactory("vm://49");
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue("boletos.eventos::boletos.eventos.bi"))) {
            connection.start();
            var message = (TextMessage) consumer.receive(3000);
            assertNotNull(message);
            assertEquals(evento, mapper.readValue(message.getText(), EventoBoleto.class));
            session.rollback();
            message = (TextMessage) consumer.receive(4000);
            assertNotNull(message);
            assertEquals(evento.idEvento().toString(), message.getStringProperty("id_evento"));
            session.commit();
        }
    }

    @Test
    void eventoCorrompidoIsolaSemBloquearBom() {
        var invalido = evento(TipoEventoBoleto.EmissaoAceita);
        repository.saveAndFlush(new EventoOutboxEntity(invalido, "{invalido"));
        var bom = evento(TipoEventoBoleto.BoletoRegistrado);
        transacao().executeWithoutResult(tx -> service.adicionar(bom));
        despachar(0, factory);
        assertEquals("EVENTO_INVALIDO", repository.findById(invalido.idEvento()).orElseThrow().getUltimoErro());
        assertNotNull(repository.findById(invalido.idEvento()).orElseThrow().getFalhaDefinitivaEm());
        assertNotNull(repository.findById(bom.idEvento()).orElseThrow().getPublicadoEm());
    }

    private void receber(EventoBoleto evento, int quantidade) throws Exception {
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(
                     BrokerApplication.EVENTOS + "::" + BrokerApplication.EVENTOS_CONSULTA))) {
            connection.start();
            for (int i = 0; i < quantidade; i++) {
                var message = (TextMessage) consumer.receive(3000);
                assertNotNull(message);
                assertEquals(evento, mapper.readValue(message.getText(), EventoBoleto.class));
                assertEquals(evento.idEvento().toString(), message.getStringProperty("id_evento"));
                assertEquals(evento.correlationId().toString(), message.getJMSCorrelationID());
                assertEquals(jakarta.jms.DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
                session.commit();
            }
            assertNull(consumer.receive(200));
        }
    }

    private EventoBoleto evento(TipoEventoBoleto tipo) {
        UUID id = UUID.randomUUID();
        return new EventoBoleto(UUID.randomUUID(), tipo, id, "123456", tipo.status(), agora(0), id);
    }

    private LocalDateTime agora(int segundos) {
        return LocalDateTime.ofInstant(inicio.plusSeconds(segundos), ZoneOffset.UTC);
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(transactionManager);
    }

    private void despachar(int segundos, ActiveMQConnectionFactory connectionFactory) {
        var jms = new JmsTemplate(connectionFactory);
        jms.setPubSubDomain(true);
        jms.setSessionTransacted(true);
        jms.setExplicitQosEnabled(true);
        jms.setDeliveryPersistent(true);
        new EventoOutboxDispatcher(repository, mapper, jms,
                Clock.fixed(inicio.plusSeconds(segundos), ZoneOffset.UTC)).publicarPendentes();
    }
}
