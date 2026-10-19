package br.com.caixa.cobranca.contracts;

import java.util.UUID;

/**
 * Comando assíncrono para registrar um boleto aceito.
 */
public record RegistrarBoletoCommand(
        UUID identificador,
        BoletoAceito aceite,
        EmitirBoletoRequest solicitacao
) {
}
