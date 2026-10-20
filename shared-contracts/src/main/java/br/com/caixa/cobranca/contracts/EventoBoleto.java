package br.com.caixa.cobranca.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventoBoleto(UUID idEvento, TipoEventoBoleto tipo, UUID identificadorBoleto,
                           String convenio, StatusBoleto status, LocalDateTime dataHora,
                           UUID correlationId) {

    public EventoBoleto {
        if (!uuidV4(idEvento) || !uuidV4(identificadorBoleto) || tipo == null
                || convenio == null || !convenio.matches("[0-9]{6}")
                || status != tipo.status() || dataHora == null || correlationId == null) {
            throw new IllegalArgumentException("Envelope de evento de boleto invalido.");
        }
    }

    private static boolean uuidV4(UUID id) {
        return id != null && id.version() == 4 && id.variant() == 2;
    }
}
