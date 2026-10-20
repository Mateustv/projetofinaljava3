package br.com.caixa.cobranca.registro.dtos;

import br.com.caixa.cobranca.contracts.StatusBoleto;

import java.time.LocalDateTime;

public record ResultadoRegistradora(
        StatusBoleto status,
        String identificador,
        LocalDateTime dataHoraRegistro,
        String codigoRejeicao,
        String motivoRejeicao,
        String linhaDigitavel,
        String codigoBarras
) {
    public ResultadoRegistradora {
        boolean registrado = status == StatusBoleto.REGISTRADO
                && identificador != null && !identificador.isBlank() && dataHoraRegistro != null
                && linhaDigitavel != null && !linhaDigitavel.isBlank()
                && codigoBarras != null && !codigoBarras.isBlank()
                && codigoRejeicao == null && motivoRejeicao == null;
        boolean rejeitado = status == StatusBoleto.REJEITADO
                && codigoRejeicao != null && !codigoRejeicao.isBlank()
                && motivoRejeicao != null && !motivoRejeicao.isBlank()
                && identificador == null && dataHoraRegistro == null
                && linhaDigitavel == null && codigoBarras == null;
        if (!registrado && !rejeitado) {
            throw new IllegalArgumentException("Resultado da registradora inconsistente.");
        }
    }
}
