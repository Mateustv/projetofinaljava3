package br.com.caixa.cobranca.emissor.services;

import br.com.caixa.cobranca.emissor.models.ComandoRegistroOutboxEntity;
import br.com.caixa.cobranca.emissor.publishers.RegistroCommandPublisher;
import br.com.caixa.cobranca.emissor.repositories.ComandoRegistroOutboxRepository;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.http.demo.Rastros;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jms.JmsException;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "emissao.outbox.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final ComandoRegistroOutboxRepository repository;
    private final RegistroCommandPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OutboxDispatcher(ComandoRegistroOutboxRepository repository,
                            RegistroCommandPublisher publisher,
                            ObjectMapper objectMapper) {
        this(repository, publisher, objectMapper, Clock.systemDefaultZone());
    }

    public OutboxDispatcher(ComandoRegistroOutboxRepository repository,
                            RegistroCommandPublisher publisher,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${emissao.outbox.dispatch.interval-ms:1000}")
    public void despacharPendentes() {
        LocalDateTime agora = agora();
        for (ComandoRegistroOutboxEntity outbox : repository.buscarElegiveis(agora, PageRequest.of(0, 20))) {
            // Itens pendentes da E4 podem ter ultrapassado o limite antes desta politica.
            if (outbox.getTentativas() >= ComandoRegistroOutboxEntity.MAX_TENTATIVAS) {
                outbox.isolar("LIMITE_TENTATIVAS", agora());
                repository.save(outbox);
                registrarFalha(outbox);
                continue;
            }
            try {
                RegistrarBoletoCommand comando =
                        outbox.getComandoJson() == null ? null
                                : objectMapper.readValue(outbox.getComandoJson(), RegistrarBoletoCommand.class);
                if (comando == null) {
                    outbox.registrarFalha("COMANDO_NULO", true, agora());
                    repository.save(outbox);
                    registrarFalha(outbox);
                    continue;
                }
                publisher.publicar(comando);
                Rastros.registrar(comando.identificador(), comando.correlationId(), "COMANDO_PUBLICADO",
                        "Commit JMS confirmado pelo Artemis; nao significa registro concluido.");
                outbox.registrarEntrega(agora());
                repository.save(outbox);
                LOGGER.info("Publicacao confirmada. identificador={}, tentativa={}, correlation_id={}",
                        outbox.getIdentificador(), outbox.getTentativas(), comando.correlationId());
            } catch (JsonProcessingException ex) {
                outbox.registrarFalha("JSON_INVALIDO", true, agora());
                repository.save(outbox);
                registrarFalha(outbox);
            } catch (JmsException ex) {
                outbox.registrarFalha(ex.getClass().getSimpleName(),
                        ex instanceof MessageConversionException, agora());
                repository.save(outbox);
                registrarFalha(outbox);
            } catch (RestClientException ex) {
                boolean permanente = false;
                String tipo = ex.getClass().getSimpleName();
                if (ex instanceof RestClientResponseException resposta) {
                    int status = resposta.getStatusCode().value();
                    permanente = status >= 400 && status < 500 && status != 408 && status != 429;
                    tipo = "HTTP_" + status;
                }
                outbox.registrarFalha(tipo, permanente, agora());
                repository.save(outbox);
                registrarFalha(outbox);
            }
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private void registrarFalha(ComandoRegistroOutboxEntity outbox) {
        Rastros.registrar(outbox.getIdentificador(), null, "FALHA_PUBLICACAO_COMANDO",
                "Tentativa " + outbox.getTentativas() + "; tipo=" + outbox.getUltimoErro()
                        + "; isolado=" + (outbox.getFalhaDefinitivaEm() != null));
        LOGGER.warn("Falha na entrega. identificador={}, tentativa={}, tipo={}, isolada={}, proximaTentativa={}",
                outbox.getIdentificador(), outbox.getTentativas(), outbox.getUltimoErro(),
                outbox.getFalhaDefinitivaEm() != null, outbox.getProximaTentativaEm());
    }
}
