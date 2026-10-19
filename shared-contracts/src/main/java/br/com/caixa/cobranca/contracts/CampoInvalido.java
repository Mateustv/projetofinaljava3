package br.com.caixa.cobranca.contracts;

/** Um campo invalido e o motivo (sem eco do valor enviado: pode ser dado pessoal). */
public record CampoInvalido(
        String campo,
        String motivo
) {
}
