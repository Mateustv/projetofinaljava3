package br.com.caixa.cobranca.registro;

import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration
@ConditionalOnProperty(name = "registro.mensageria.enabled", havingValue = "true", matchIfMissing = true)
public class RegistroJmsConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroJmsConfiguration.class);

    @Bean
    public DefaultJmsListenerContainerFactory registroJmsFactory(ConnectionFactory connectionFactory) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1");
        factory.setRecoveryInterval(2000L);
        factory.setErrorHandler(ex -> LOGGER.warn("Entrega JMS revertida. tipo={}",
                NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName()));
        return factory;
    }
}
