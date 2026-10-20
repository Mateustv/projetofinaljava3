package br.com.caixa.cobranca.eventos;

import br.com.caixa.cobranca.contracts.EventoBoleto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class EventoOutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventoOutboxDispatcher.class);
    private final EventoOutboxRepository repository;
    private final ObjectMapper mapper;
    private final JmsTemplate jms;
    private final Clock clock;

    public EventoOutboxDispatcher(EventoOutboxRepository repository, ObjectMapper mapper,
                                 JmsTemplate jms, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.jms = jms;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eventos.outbox.interval-ms:1000}")
    public void publicarPendentes() {
        for (var item : repository.buscarElegiveis(agora(), PageRequest.of(0, 20))) {
            try {
                var evento = mapper.readValue(item.getEventoJson(), EventoBoleto.class);
                if (evento == null || !item.getIdEvento().equals(evento.idEvento())) {
                    throw new IllegalArgumentException("Evento inconsistente com outbox.");
                }
                jms.send("boletos.eventos", session -> {
                    try (var browser = session.createBrowser(session.createQueue(
                            "boletos.eventos::boletos.eventos.consulta"))) {
                        browser.getEnumeration();
                    }
                    var message = session.createTextMessage(item.getEventoJson());
                    message.setJMSCorrelationID(evento.correlationId().toString());
                    message.setStringProperty("id_evento", evento.idEvento().toString());
                    message.setStringProperty("tipo", evento.tipo().name());
                    return message;
                });
                item.confirmar(agora());
                LOGGER.info("Evento publicado. idEvento={}, identificador={}, tentativa={}",
                        item.getIdEvento(), item.getIdentificadorBoleto(), item.getTentativas());
            } catch (JsonProcessingException | IllegalArgumentException ex) {
                item.falhar("EVENTO_INVALIDO", true, agora());
                registrarFalha(item);
            } catch (JmsException ex) {
                item.falhar(ex.getClass().getSimpleName(), false, agora());
                registrarFalha(item);
            }
            repository.save(item);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private void registrarFalha(EventoOutboxEntity item) {
        LOGGER.warn("Falha ao publicar evento. idEvento={}, tentativa={}, tipo={}, isolado={}",
                item.getIdEvento(), item.getTentativas(), item.getUltimoErro(),
                item.getFalhaDefinitivaEm() != null);
    }
}
