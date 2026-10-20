package br.com.caixa.cobranca.consulta.configs;

import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration
@ConditionalOnProperty(name = "consulta.eventos.enabled", havingValue = "true", matchIfMissing = true)
public class ConsultaEventosConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsultaEventosConfiguration.class);

    @Bean
    DefaultJmsListenerContainerFactory consultaEventosFactory(ConnectionFactory factory) {
        var listener = new DefaultJmsListenerContainerFactory();
        listener.setConnectionFactory(factory);
        listener.setSessionTransacted(true);
        listener.setConcurrency("1");
        listener.setRecoveryInterval(2000L);
        listener.setErrorHandler(ex -> LOGGER.warn("Evento nao confirmado. tipo={}",
                NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName()));
        return listener;
    }
}
