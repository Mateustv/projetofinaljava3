package br.com.caixa.cobranca.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projecao de leitura da consulta. Campos de pagamento so sao preenchidos em REGISTRADO;
 * dados de rejeicao so sao preenchidos em REJEITADO.
 */
public record BoletoConsulta(
        UUID identificador,
        StatusBoleto status,
        LocalDateTime dataHoraRecebimento,
        String linhaDigitavel,
        String codigoBarras,
        String identificadorRegistradora,
        LocalDateTime dataHoraRegistro,
        String codigoRejeicao,
        String motivoRejeicao
) {

    public static BoletoConsulta aguardando(BoletoAceito aceite) {
        return new BoletoConsulta(
                aceite.identificador(),
                StatusBoleto.AGUARDANDO_REGISTRO,
                aceite.dataHoraRecebimento(),
                null, null, null, null, null, null
        );
    }

    public static BoletoConsulta aguardando(BoletoConsulta aceite) {
        return new BoletoConsulta(
                aceite.identificador(),
                StatusBoleto.AGUARDANDO_REGISTRO,
                aceite.dataHoraRecebimento(),
                null, null, null, null, null, null
        );
    }

    public static BoletoConsulta somenteEstado(BoletoConsulta fonte) {
        return switch (fonte.status()) {
            case AGUARDANDO_REGISTRO -> new BoletoConsulta(
                    fonte.identificador(), fonte.status(), fonte.dataHoraRecebimento(),
                    null, null, null, null, null, null
            );
            case REGISTRADO -> new BoletoConsulta(
                    fonte.identificador(), fonte.status(), fonte.dataHoraRecebimento(),
                    fonte.linhaDigitavel(), fonte.codigoBarras(), fonte.identificadorRegistradora(),
                    fonte.dataHoraRegistro(), null, null
            );
            case REJEITADO -> new BoletoConsulta(
                    fonte.identificador(), fonte.status(), fonte.dataHoraRecebimento(),
                    null, null, null, null, fonte.codigoRejeicao(), fonte.motivoRejeicao()
            );
        };
    }
}
