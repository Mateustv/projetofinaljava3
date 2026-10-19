package br.com.caixa.cobranca.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resposta do POST /boletos (HTTP 202 Accepted): "aceita para processamento",
 * NUNCA "registrado". Sem linha digitavel/codigo de barras (so apos o registro).
 */
public record BoletoAceito(
        UUID identificador,
        StatusBoleto status,
        LocalDateTime dataHoraRecebimento
) {
}
