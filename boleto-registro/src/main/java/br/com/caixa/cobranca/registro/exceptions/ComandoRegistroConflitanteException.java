package br.com.caixa.cobranca.registro.exceptions;

public class ComandoRegistroConflitanteException extends RuntimeException {

    public ComandoRegistroConflitanteException() {
        super("O identificador ja foi processado com outro conteudo.");
    }
}
