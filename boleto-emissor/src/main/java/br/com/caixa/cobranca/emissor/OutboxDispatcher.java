package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "emissao.outbox.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final ComandoRegistroOutboxRepository repository;
    private final RegistroCommandPublisher publisher;
    private final ObjectMapper objectMapper;

    public OutboxDispatcher(ComandoRegistroOutboxRepository repository,
                            RegistroCommandPublisher publisher,
                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${emissao.outbox.dispatch.interval-ms:1000}")
    public void despacharPendentes() {
        for (ComandoRegistroOutboxEntity outbox : repository.findTop20ByEntregueEmIsNullOrderByCriadoEmAsc()) {
            try {
                RegistrarBoletoCommand comando =
                        objectMapper.readValue(outbox.getComandoJson(), RegistrarBoletoCommand.class);
                publisher.publicar(comando);
                outbox.registrarEntrega(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
                repository.save(outbox);
            } catch (RestClientException | JsonProcessingException ex) {
                outbox.registrarFalha(ex.getClass().getSimpleName());
                repository.save(outbox);
                LOGGER.warn("Falha ao entregar comando de registro. identificador={}, tipo={}",
                        outbox.getIdentificador(), ex.getClass().getSimpleName());
            }
        }
    }
}
