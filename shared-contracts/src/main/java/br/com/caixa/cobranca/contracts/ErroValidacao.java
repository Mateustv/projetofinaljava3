package br.com.caixa.cobranca.contracts;

import java.util.List;

/** Corpo do HTTP 400: TODOS os campos invalidos de uma vez (sessao 2). */
public record ErroValidacao(
        List<CampoInvalido> erros
) {
}
