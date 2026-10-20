package br.com.caixa.cobranca.emissor.exceptions;

public class ChaveIdempotenciaConflitanteException extends RuntimeException {

    public ChaveIdempotenciaConflitanteException() {
        super("A chave de idempotencia ja foi aceita com outro conteudo.");
    }
}
