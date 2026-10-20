package br.com.caixa.cobranca.broker;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BrokerApplication {

    public static final String COMANDOS = "registro.comandos";
    public static final String QUARENTENA = "registro.quarentena";
    public static final String EVENTOS = "boletos.eventos";
    public static final String EVENTOS_CONSULTA = "boletos.eventos.consulta";
    public static final String EVENTOS_QUARENTENA = "boletos.eventos.quarentena";

    private BrokerApplication() {
    }

    public static ActiveMQServer criar(Path dados, String acceptor) throws Exception {
        var configuration = new ConfigurationImpl();
        configuration.setName("central-cobranca");
        configuration.setSecurityEnabled(false);
        configuration.setPersistenceEnabled(true);
        configuration.setJournalType(JournalType.NIO);
        configuration.setJournalDirectory(dados.resolve("journal").toString());
        configuration.setBindingsDirectory(dados.resolve("bindings").toString());
        configuration.setPagingDirectory(dados.resolve("paging").toString());
        configuration.setLargeMessagesDirectory(dados.resolve("large-messages").toString());
        configuration.setJournalSyncTransactional(true);
        configuration.setJournalSyncNonTransactional(true);
        configuration.setPersistDeliveryCountBeforeDelivery(true);
        configuration.setJMXManagementEnabled(false);
        configuration.addAcceptorConfiguration("local", acceptor);
        configuration.addAddressSetting("#", new AddressSettings()
                .setAutoCreateAddresses(false).setAutoCreateQueues(false)
                .setAutoDeleteAddresses(false).setAutoDeleteQueues(false)
                .setSendToDLAOnNoRoute(false));
        configuration.addAddressSetting(COMANDOS, new AddressSettings()
                .setAutoCreateAddresses(false).setAutoCreateQueues(false)
                .setAutoDeleteAddresses(false).setAutoDeleteQueues(false)
                .setDeadLetterAddress(new SimpleString(QUARENTENA))
                .setSendToDLAOnNoRoute(true)
                .setMaxDeliveryAttempts(3)
                .setRedeliveryDelay(1000).setRedeliveryMultiplier(5.0).setMaxRedeliveryDelay(5000));
        configuration.addQueueConfiguration(new QueueConfiguration(COMANDOS)
                .setAddress(COMANDOS).setRoutingType(RoutingType.ANYCAST).setDurable(true));
        configuration.addQueueConfiguration(new QueueConfiguration(QUARENTENA)
                .setAddress(QUARENTENA).setRoutingType(RoutingType.ANYCAST).setDurable(true));
        configuration.addAddressSetting(EVENTOS, new AddressSettings()
                .setAutoCreateAddresses(false).setAutoCreateQueues(false)
                .setAutoDeleteAddresses(false).setAutoDeleteQueues(false)
                .setDeadLetterAddress(new SimpleString(EVENTOS_QUARENTENA))
                .setSendToDLAOnNoRoute(true)
                .setMaxDeliveryAttempts(3)
                .setRedeliveryDelay(1000).setRedeliveryMultiplier(5.0).setMaxRedeliveryDelay(5000));
        configuration.addAddressConfiguration(new CoreAddressConfiguration()
                .setName(EVENTOS).addRoutingType(RoutingType.MULTICAST)
                .addQueueConfiguration(new QueueConfiguration(EVENTOS_CONSULTA)
                        .setAddress(EVENTOS).setRoutingType(RoutingType.MULTICAST).setDurable(true)));
        configuration.addQueueConfiguration(new QueueConfiguration(EVENTOS_QUARENTENA)
                .setAddress(EVENTOS_QUARENTENA).setRoutingType(RoutingType.ANYCAST).setDurable(true));
        return ActiveMQServers.newActiveMQServer(configuration);
    }

    public static void main(String[] args) throws Exception {
        String porta = System.getProperty("broker.port", "61616");
        String diretorio = System.getProperty("broker.data", "data/broker");
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                porta = arg.substring("--port=".length());
            } else if (arg.startsWith("--data=")) {
                diretorio = arg.substring("--data=".length());
            } else {
                throw new IllegalArgumentException("Argumento desconhecido; use --port=numero e --data=diretorio.");
            }
        }
        int port = Integer.parseInt(porta);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("broker.port deve estar entre 1 e 65535.");
        }
        if (diretorio.isBlank()) {
            throw new IllegalArgumentException("broker.data nao pode ser vazio.");
        }
        Path dados = Path.of(diretorio).toAbsolutePath().normalize();
        System.setProperty("broker.data", dados.toString());
        Logger logger = LoggerFactory.getLogger(BrokerApplication.class);
        var server = criar(dados,
                "tcp://127.0.0.1:" + port + "?protocols=CORE");
        var encerrado = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ex) {
                logger.error("Falha ao encerrar broker.", ex);
            } finally {
                encerrado.countDown();
            }
        }, "broker-shutdown"));
        server.start();
        if (!server.waitForActivation(10, TimeUnit.SECONDS)) {
            server.stop();
            throw new IllegalStateException("Broker nao ficou ativo; consulte o log de inicializacao.");
        }
        logger.info("Broker pronto em 127.0.0.1:{}. Filas duraveis: {}, {}, {}, {}.",
                port, COMANDOS, QUARENTENA, EVENTOS_CONSULTA, EVENTOS_QUARENTENA);
        encerrado.await();
    }
}
