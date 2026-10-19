package br.com.caixa.cobranca.registro;

import java.time.LocalDateTime;

public record ResultadoRegistradora(String identificador, LocalDateTime dataHoraRegistro) {
}
