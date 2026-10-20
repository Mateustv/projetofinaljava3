package br.com.caixa.cobranca.broker;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BrokerPersistenceTest {

    @TempDir
    Path dados;
    private ActiveMQServer server;

    @BeforeEach
    void iniciar() throws Exception {
        server = BrokerApplication.criar(dados, "vm://41");
        server.start();
    }

    @AfterEach
    void parar() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void mensagemConfirmadaSobreviveAReinicioETransacaoSemCommitNaoEntra() throws Exception {
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            try (var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS))) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                producer.send(session.createTextMessage("confirmada"));
                session.commit();
                producer.send(session.createTextMessage("nao-confirmada"));
                session.rollback();
            }
        }
        server.stop();
        server = BrokerApplication.criar(dados, "vm://41");
        server.start();
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            assertEquals("confirmada", ((TextMessage) consumer.receive(3000)).getText());
            session.commit();
            assertNull(consumer.receive(200));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"--port=0", "--port=65536", "--port=abc", "--data=", "--desconhecido"})
    void argumentoInvalidoFalhaExplicitamenteAntesDeIniciar(String argumento) {
        assertThrows(IllegalArgumentException.class, () -> BrokerApplication.main(new String[]{argumento}));
    }

    @Test
    void contadorDeEntregaNaoZeraAoReiniciarBroker() throws Exception {
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS));
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            producer.send(session.createTextMessage("pendente"));
            session.commit();
            connection.start();
            var message = consumer.receive(3000);
            assertNotNull(message);
            assertEquals(1, message.getIntProperty("JMSXDeliveryCount"));
            session.rollback();
        }
        server.stop();
        server = BrokerApplication.criar(dados, "vm://41");
        server.start();
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var message = consumer.receive(4000);
            assertNotNull(message);
            assertEquals(2, message.getIntProperty("JMSXDeliveryCount"));
            session.commit();
        }
    }

    @Test
    void destinoNaoProvisionadoFalhaSemCriacaoSilenciosa() throws Exception {
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            assertThrows(jakarta.jms.JMSException.class, () -> {
                try (var producer = session.createProducer(session.createQueue("fila.inexistente"))) {
                    producer.send(session.createTextMessage("nao-descartar"));
                    session.commit();
                }

            });
        }
    }

    @Test
    void aceitaConexaoTcpParaClientesEmOutrosProcessos() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        server.stop();
        server = BrokerApplication.criar(dados, "tcp://127.0.0.1:" + port + "?protocols=CORE");
        server.start();
        try (var factory = new ActiveMQConnectionFactory(
                "tcp://127.0.0.1:" + port + "?callTimeout=2000&initialConnectAttempts=0&reconnectAttempts=0");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            assertNotNull(session.createQueue(BrokerApplication.COMANDOS));
        }
    }

    @Test
    void mensagemSemBindingNaoEDescartadaPeloBroker() throws Exception {
        String endereco = "comandos.sem-binding";
        server.addAddressInfo(new AddressInfo(new SimpleString(endereco), RoutingType.ANYCAST));
        server.getAddressSettingsRepository().addMatch(endereco,
                server.getAddressSettingsRepository().getMatch(BrokerApplication.COMANDOS));
        try (var locator = ActiveMQClient.createServerLocator("vm://41");
             var factory = locator.createSessionFactory();
             var session = factory.createSession(false, false, false);
             var producer = session.createProducer(endereco);
             var dlq = session.createConsumer(BrokerApplication.QUARENTENA)) {
            var enviada = session.createMessage(true);
            enviada.getBodyBuffer().writeString("sem-rota");
            producer.send(enviada);
            session.commit();
            session.start();
            var message = dlq.receive(3000);
            assertNotNull(message);
            assertEquals("sem-rota", message.getBodyBuffer().readString());
            assertEquals(endereco, message.getStringProperty("_AMQ_ORIG_ADDRESS"));
            message.acknowledge();
            session.commit();
        }
    }

    @Test
    void redeliveryTemTresTentativasComBackoffEQuarentenaPersistente() throws Exception {
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createQueue(BrokerApplication.COMANDOS));
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            producer.send(session.createTextMessage("falha-temporaria"));
            session.commit();
            connection.start();
            long ultimoRollback = 0;
            for (int tentativa = 1; tentativa <= 3; tentativa++) {
                var message = consumer.receive(8000);
                assertNotNull(message);
                assertEquals(tentativa, message.getIntProperty("JMSXDeliveryCount"));
                if (tentativa > 1) {
                    long esperaMinima = TimeUnit.SECONDS.toNanos(tentativa == 2 ? 1 : 5);
                    assertTrue(System.nanoTime() - ultimoRollback >= esperaMinima,
                            "Redelivery antecipado em relacao ao backoff minimo.");
                }
                ultimoRollback = System.nanoTime();
                session.rollback();
            }
        }
        server.stop();
        server = BrokerApplication.criar(dados, "vm://41");
        server.start();
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var dlq = session.createConsumer(session.createQueue(BrokerApplication.QUARENTENA));
             var consumer = session.createConsumer(session.createQueue(BrokerApplication.COMANDOS))) {
            connection.start();
            var message = (TextMessage) dlq.receive(3000);
            assertNotNull(message);
            assertEquals("falha-temporaria", message.getText());
            assertEquals(BrokerApplication.COMANDOS, message.getStringProperty("_AMQ_ORIG_ADDRESS"));
            session.commit();
            assertNull(consumer.receive(200));
        }
    }

    @Test
    void pubsubDuravelPreservaAssinanteOfflineSemBloquearOutro() throws Exception {
        server.addAddressInfo(new AddressInfo(new SimpleString("eventos.teste"), RoutingType.MULTICAST));
        for (String nome : new String[]{"eventos.teste.consulta", "eventos.teste.bi"}) {
            server.createQueue(new QueueConfiguration(nome).setAddress("eventos.teste")
                    .setRoutingType(RoutingType.MULTICAST).setDurable(true));
        }
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createTopic("eventos.teste"));
             var consulta = session.createConsumer(session.createQueue("eventos.teste::eventos.teste.consulta"))) {
            producer.send(session.createTextMessage("evento"));
            session.commit();
            connection.start();
            assertEquals("evento", ((TextMessage) consulta.receive(3000)).getText());
            session.commit();
        }
        server.stop();
        server = BrokerApplication.criar(dados, "vm://41");
        server.start();
        try (var factory = new ActiveMQConnectionFactory("vm://41");
             var connection = factory.createConnection();
             var session = connection.createSession(true, Session.SESSION_TRANSACTED);
             var bi = session.createConsumer(session.createQueue("eventos.teste::eventos.teste.bi"))) {
            connection.start();
            assertEquals("evento", ((TextMessage) bi.receive(3000)).getText());
            session.commit();
            assertNull(bi.receive(200));
        }
    }
}
