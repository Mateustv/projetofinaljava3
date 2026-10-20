package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.StatusBoleto;

import java.time.LocalDateTime;

public record ResultadoRegistradora(
        StatusBoleto status,
        String identificador,
        LocalDateTime dataHoraRegistro,
        String codigoRejeicao,
        String motivoRejeicao
) {
    public ResultadoRegistradora {
        boolean registrado = status == StatusBoleto.REGISTRADO
                && identificador != null && !identificador.isBlank() && dataHoraRegistro != null
                && codigoRejeicao == null && motivoRejeicao == null;
        boolean rejeitado = status == StatusBoleto.REJEITADO
                && codigoRejeicao != null && !codigoRejeicao.isBlank()
                && motivoRejeicao != null && !motivoRejeicao.isBlank()
                && identificador == null && dataHoraRegistro == null;
        if (!registrado && !rejeitado) {
            throw new IllegalArgumentException("Resultado da registradora inconsistente.");
        }
    }
}
