package br.com.caixa.cobranca.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EntityScan("br.com.caixa.cobranca")
@EnableJpaRepositories("br.com.caixa.cobranca")
public class EventosOutboxConfiguration {

    @Bean
    EventoOutboxService eventoOutboxService(EventoOutboxRepository repository, ObjectMapper mapper) {
        return new EventoOutboxService(repository, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "eventos.outbox.enabled", havingValue = "true", matchIfMissing = true)
    EventoOutboxDispatcher eventoOutboxDispatcher(EventoOutboxRepository repository, ObjectMapper mapper,
                                                 ConnectionFactory factory) {
        var jms = new JmsTemplate(factory);
        jms.setPubSubDomain(true);
        jms.setSessionTransacted(true);
        jms.setExplicitQosEnabled(true);
        jms.setDeliveryPersistent(true);
        return new EventoOutboxDispatcher(repository, mapper, jms, Clock.systemDefaultZone());
    }
}
