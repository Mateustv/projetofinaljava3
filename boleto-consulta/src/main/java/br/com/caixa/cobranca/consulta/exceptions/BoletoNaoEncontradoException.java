package br.com.caixa.cobranca.consulta.exceptions;

public class BoletoNaoEncontradoException extends RuntimeException {

    public BoletoNaoEncontradoException() {
        super("Boleto nao encontrado.");
    }
}
