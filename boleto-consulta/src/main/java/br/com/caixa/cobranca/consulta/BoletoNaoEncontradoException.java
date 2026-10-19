package br.com.caixa.cobranca.consulta;

public class BoletoNaoEncontradoException extends RuntimeException {

    public BoletoNaoEncontradoException() {
        super("Boleto nao encontrado.");
    }
}
