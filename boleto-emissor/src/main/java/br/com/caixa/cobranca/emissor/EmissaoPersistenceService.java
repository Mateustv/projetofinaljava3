package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
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

    public EmissaoPersistenceService(EmissaoAceitaRepository emissaoRepository,
                                     ComandoRegistroOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.emissaoRepository = emissaoRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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

        RegistrarBoletoCommand comando = new RegistrarBoletoCommand(aceite.identificador(), aceite, request);
        try {
            outboxRepository.saveAndFlush(new ComandoRegistroOutboxEntity(
                    aceite.identificador(),
                    objectMapper.writeValueAsString(comando),
                    recebimento
            ));
        } catch (JsonProcessingException ex) {
            throw new FalhaAoAceitarEmissaoException(ex);
        }

        return aceite;
    }
}
