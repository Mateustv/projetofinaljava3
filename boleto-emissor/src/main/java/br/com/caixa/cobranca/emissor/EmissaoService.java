package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.CampoInvalido;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bounded context EMISSAO.
 *
 * E1 (esta etapa): valida e devolve o aceite. AINDA NAO persiste o aceite, nao verifica
 * idempotencia e nao publica na fila:
 *   - E2: persistencia do aceite + idempotencia (convenio + numero de controle) + 409
 *   - E4: publicacao do comando de registro na fila
 */
@Service
public class EmissaoService {

    private final EmissaoValidator validator;

    public EmissaoService(EmissaoValidator validator) {
        this.validator = validator;
    }

    public BoletoAceito aceitar(EmitirBoletoRequest request) {
        List<CampoInvalido> erros = validator.validar(request);
        if (!erros.isEmpty()) {
            throw new EmissaoInvalidaException(erros);
        }
        return new BoletoAceito(UUID.randomUUID(), StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.now());
    }
}
