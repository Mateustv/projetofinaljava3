package br.com.caixa.cobranca.emissor.services;

import br.com.caixa.cobranca.emissor.exceptions.FalhaAoAceitarEmissaoException;
import br.com.caixa.cobranca.emissor.models.ComandoRegistroOutboxEntity;
import br.com.caixa.cobranca.emissor.models.EmissaoAceitaEntity;
import br.com.caixa.cobranca.emissor.repositories.ComandoRegistroOutboxRepository;
import br.com.caixa.cobranca.emissor.repositories.EmissaoAceitaRepository;
import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import br.com.caixa.cobranca.eventos.EventoOutboxService;
import br.com.caixa.cobranca.http.Correlacao;
import br.com.caixa.cobranca.http.demo.Rastros;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class EmissaoPersistenceService {

    private final EmissaoAceitaRepository emissaoRepository;
    private final ComandoRegistroOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final EventoOutboxService eventos;

    public EmissaoPersistenceService(EmissaoAceitaRepository emissaoRepository,
                                     ComandoRegistroOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper, EventoOutboxService eventos) {
        this.emissaoRepository = emissaoRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.eventos = eventos;
    }

    @Transactional
    public BoletoAceito persistir(EmitirBoletoRequest request,
                                  String fingerprint,
                                  String solicitacaoJson) {
        LocalDateTime recebimento = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        BoletoAceito aceite = new BoletoAceito(UUID.randomUUID(),
                br.com.caixa.cobranca.contracts.StatusBoleto.AGUARDANDO_REGISTRO, recebimento);

        emissaoRepository.saveAndFlush(new EmissaoAceitaEntity(
                aceite.identificador(),
                request.convenio(),
                request.numeroControleCliente(),
                fingerprint,
                solicitacaoJson,
                recebimento
        ));

        UUID correlationId = Correlacao.atualOu(aceite.identificador());
        RegistrarBoletoCommand comando = new RegistrarBoletoCommand(
                aceite.identificador(), aceite, request, correlationId);
        try {
            outboxRepository.saveAndFlush(new ComandoRegistroOutboxEntity(
                    aceite.identificador(),
                    objectMapper.writeValueAsString(comando),
                    recebimento
            ));
        } catch (JsonProcessingException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }

        eventos.adicionar(new EventoBoleto(UUID.randomUUID(), TipoEventoBoleto.EmissaoAceita,
                aceite.identificador(), request.convenio(), aceite.status(), recebimento, correlationId));
        Rastros.aposCommit(aceite.identificador(), correlationId, "ACEITE_PERSISTIDO",
                "Commit H2: aceite + comando outbox + intencao EmissaoAceita.");
        return aceite;
    }
}
