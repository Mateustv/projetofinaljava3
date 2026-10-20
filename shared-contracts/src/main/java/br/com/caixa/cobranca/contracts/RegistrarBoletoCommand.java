package br.com.caixa.cobranca.contracts;

import java.util.UUID;

/**
 * Comando assíncrono para registrar um boleto aceito.
 */
public record RegistrarBoletoCommand(
        UUID identificador,
        BoletoAceito aceite,
        EmitirBoletoRequest solicitacao,
        UUID correlationId
) {
    public RegistrarBoletoCommand {
        // Pendencias anteriores a E10 nao tinham correlacao explicita.
        if (correlationId == null) {
            correlationId = identificador;
        }
    }

    public RegistrarBoletoCommand(UUID identificador, BoletoAceito aceite, EmitirBoletoRequest solicitacao) {
        this(identificador, aceite, solicitacao, identificador);
    }
}
