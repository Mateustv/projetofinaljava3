package br.com.caixa.cobranca.registro;

public class ComandoRegistroInvalidoException extends RuntimeException {

    public ComandoRegistroInvalidoException() {
        super("O comando de registro e invalido.");
    }
}
