package br.com.caixa.cobranca.emissor;

public class ChaveIdempotenciaConflitanteException extends RuntimeException {

    public ChaveIdempotenciaConflitanteException() {
        super("A chave de idempotencia ja foi aceita com outro conteudo.");
    }
}
