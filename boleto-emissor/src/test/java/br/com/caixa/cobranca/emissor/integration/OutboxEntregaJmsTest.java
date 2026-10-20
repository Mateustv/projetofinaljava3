package br.com.caixa.cobranca.emissor.integration;

import br.com.caixa.cobranca.emissor.models.ComandoRegistroOutboxEntity;
import br.com.caixa.cobranca.emissor.publishers.JmsRegistroCommandPublisher;
import br.com.caixa.cobranca.emissor.publishers.RegistroCommandPublisher;
import br.com.caixa.cobranca.emissor.repositories.ComandoRegistroOutboxRepository;
import br.com.caixa.cobranca.emissor.services.OutboxDispatcher;
import br.com.caixa.cobranca.broker.BrokerApplication;
import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.eventos.EventoOutboxRepository;
import br.com.caixa.cobranca.http.Correlacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jms.core.JmsTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:outbox-jms;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class OutboxEntregaJmsTest {

    @TempDir
    Path dados;
    @Autowired
    ComandoRegistroOutboxRepository repository;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    MockMvc mvc;
    @Autowired
    EventoOutboxRepository eventos;
    private ActiveMQServer server;
    private ActiveMQConnectionFactory factory;
    private RegistroCommandPublisher publisher;
    private final Instant inicio = Instant.parse("2026-10-20T12:00:00Z");

    @BeforeEach
    void iniciar() throws Exception {
        repository.deleteAll();
        server = BrokerApplication.criar(dados, "vm://42");
        server.start();
        factory = new ActiveMQConnectionFactory("vm://42");
        publisher = new JmsRegistroCommandPublisher(new JmsTemplate(factory), mapper, BrokerApplication.COMANDOS);
    }

    @AfterEach
    void parar() throws Exception {
        factory.close();
        server.stop();
    }

    @Test
    void correlacaoHttpPersisteNasDuasIntencoesEJmsSemAlterarIdempotencia() throws Exception {
        UUID correlation = UUID.randomUUID();
        String corpo = BoletoControllerTest.corpo("123456",
                "E10-" + UUID.randomUUID().toString().substring(0, 12), "150.00");
        var response = mvc.perform(post("/boletos").contentType("application/json").content(corpo)
                        .header(Correlacao.HEADER, correlation.toString()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(Correlacao.HEADER, correlation.toString())).andReturn();
        UUID id = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).get("identificador").asText());
        var comando = mapper.readValue(repository.findById(id).orElseThrow().getComandoJson(), RegistrarBoletoCommand.class);
        assertEquals(correlation, comando.correlationId());
        var evento = eventos.findAll().stream()
                .filter(item -> item.getIdentificadorBoleto().equals(id)).findFirst().orElseThrow();
        assertEquals(correlation, mapper.readValue(evento.getEventoJson(), EventoBoleto.class).correlationId());
        UUID repeticao = UUID.randomUUID();
        mvc.perform(post("/boletos").contentType("application/json").content(corpo)
                        .header(Correlacao.HEADER, repeticao.toString()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(Correlacao.HEADER, repeticao.toString()))
                .andExpect(jsonPath("$.identificador").value(id.toString()));
        assertEquals(1, repository.count());
        assertEquals(correlation, mapper.readValue(repository.findById(id).orElseThrow()
                .getComandoJson(), RegistrarBoletoCommand.class).correlationId());
        publisher.publicar(comando);
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var message = (TextMessage) consumer.receive(3000);
            assertNotNull(message);
            assertEquals(correlation.toString(), message.getJMSCorrelationID());
            assertEquals(comando, mapper.readValue(message.getText(), RegistrarBoletoCommand.class));
            session.commit();
        }
        assertNull(Correlacao.atualOu(null));
    }

    @Test
    void confirmaPublicacaoAntesDoRegistroESobreviveAReinicio() throws Exception {
        UUID id = criar();
        despachar(0, publisher);
        assertNotNull(repository.findById(id).orElseThrow().getEntregueEm());
        assertNull(repository.findById(id).orElseThrow().getComandoJson());
        factory.close();
        server.stop();
        server = BrokerApplication.criar(dados, "vm://42");
        server.start();
        factory = new ActiveMQConnectionFactory("vm://42");
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var message = (TextMessage) consumer.receive(3000);
            assertNotNull(message);
            assertEquals(id.toString(), message.getJMSCorrelationID());
            assertEquals(id, mapper.readValue(message.getText(), RegistrarBoletoCommand.class).identificador());
            session.commit();
        }
    }

    @Test
    void destinoErradoNaoConcluiOutboxERecuperaComDestinoCorreto() throws Exception {
        UUID id = criar();
        var errado = new JmsRegistroCommandPublisher(new JmsTemplate(factory), mapper, "fila.inexistente");
        despachar(0, errado);
        var falha = repository.findById(id).orElseThrow();
        assertNull(falha.getEntregueEm());
        assertNotNull(falha.getComandoJson());
        assertEquals(1, falha.getTentativas());
        despachar(1, publisher);
        assertNotNull(repository.findById(id).orElseThrow().getEntregueEm());
    }

    @Test
    void enderecoSemFilaNaoPodeConfirmarPublicacaoSemRota() throws Exception {
        UUID id = criar();
        server.destroyQueue(new SimpleString(BrokerApplication.COMANDOS), null, false, true, false);
        assertNotNull(server.getAddressInfo(new SimpleString(BrokerApplication.COMANDOS)));
        despachar(0, publisher);
        var falha = repository.findById(id).orElseThrow();
        assertNull(falha.getEntregueEm());
        assertNotNull(falha.getComandoJson());
        assertEquals(1, falha.getTentativas());
    }

    @Test
    void indisponibilidadeDoBrokerPreservaPayloadEAposTresFalhasIsola() throws Exception {
        UUID id = criar();
        server.stop();
        try (var offline = new ActiveMQConnectionFactory(
                "tcp://127.0.0.1:1?callTimeout=300&initialConnectAttempts=0&reconnectAttempts=0")) {
            var indisponivel = new JmsRegistroCommandPublisher(new JmsTemplate(offline), mapper, BrokerApplication.COMANDOS);
            despachar(0, indisponivel);
            despachar(1, indisponivel);
            despachar(6, indisponivel);
            var falha = repository.findById(id).orElseThrow();
            assertEquals(3, falha.getTentativas());
            assertNotNull(falha.getFalhaDefinitivaEm());
            assertNotNull(falha.getComandoJson());
            assertNull(falha.getEntregueEm());
        }
    }

    @Test
    void falhaAposConfirmacaoDoBrokerPodeDuplicarMensagemMasPreservaUuid() throws Exception {
        UUID id = criar();
        RegistroCommandPublisher respostaPerdida = comando -> {
            publisher.publicar(comando);
            throw new org.springframework.jms.UncategorizedJmsException("Confirmacao perdida.");
        };
        despachar(0, respostaPerdida);
        assertNull(repository.findById(id).orElseThrow().getEntregueEm());
        despachar(1, publisher);
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            for (int i = 0; i < 2; i++) {
                var message = (TextMessage) consumer.receive(3000);
                assertNotNull(message);
                assertEquals(id.toString(), message.getJMSCorrelationID());
                session.commit();
            }
            assertNull(consumer.receive(200));
        }
    }

    private UUID criar() throws Exception {
        UUID id = UUID.randomUUID();
        var comando = new RegistrarBoletoCommand(id, new BoletoAceito(
                id, StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.ofInstant(inicio, ZoneOffset.UTC)), null);
        repository.saveAndFlush(new ComandoRegistroOutboxEntity(id, mapper.writeValueAsString(comando),
                LocalDateTime.ofInstant(inicio, ZoneOffset.UTC)));
        return id;
    }

    @Test
    void migracaoPreservaHorariosContadoresIsoladosEEntreguesHttp() throws Exception {
        UUID pendente = criar();
        UUID isolado = criar();
        UUID entregueHttp = criar();
        var antigo = repository.findById(pendente).orElseThrow();
        var criadoEm = antigo.getCriadoEm();
        antigo.registrarFalha("HTTP_503", false, criadoEm);
        repository.saveAndFlush(antigo);
        var falha = repository.findById(isolado).orElseThrow();
        falha.registrarFalha("HTTP_400", true, criadoEm);
        repository.saveAndFlush(falha);
        var entregue = repository.findById(entregueHttp).orElseThrow();
        entregue.registrarEntrega(criadoEm);
        repository.saveAndFlush(entregue);

        despachar(0, publisher);
        assertNull(repository.findById(pendente).orElseThrow().getEntregueEm());
        despachar(1, publisher);
        var migrado = repository.findById(pendente).orElseThrow();
        assertEquals(criadoEm, migrado.getCriadoEm());
        assertEquals(2, migrado.getTentativas());
        assertNotNull(migrado.getEntregueEm());
        assertEquals(criadoEm, repository.findById(isolado).orElseThrow().getFalhaDefinitivaEm());
        assertEquals(1, repository.findById(isolado).orElseThrow().getTentativas());
        assertEquals(criadoEm, repository.findById(entregueHttp).orElseThrow().getEntregueEm());
        assertEquals(1, repository.findById(entregueHttp).orElseThrow().getTentativas());
        try (var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var message = consumer.receive(3000);
            assertNotNull(message);
            assertEquals(pendente.toString(), message.getJMSCorrelationID());
            session.commit();
            assertNull(consumer.receive(200));
        }
    }

    private void despachar(int segundos, RegistroCommandPublisher transporte) {
        new OutboxDispatcher(repository, transporte, mapper,
                Clock.fixed(inicio.plusSeconds(segundos), ZoneOffset.UTC)).despacharPendentes();
    }
}
