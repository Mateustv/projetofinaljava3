package br.com.caixa.cobranca.eventos;

import br.com.caixa.cobranca.contracts.EventoBoleto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class EventoOutboxService {

    private final EventoOutboxRepository repository;
    private final ObjectMapper mapper;

    public EventoOutboxService(EventoOutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void adicionar(EventoBoleto evento) {
        try {
            repository.saveAndFlush(new EventoOutboxEntity(evento, mapper.writeValueAsString(evento)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar evento para outbox.", ex);
        }
    }
}
