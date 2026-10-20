package br.com.caixa.cobranca.registro.exceptions;

public class FalhaAoRegistrarBoletoException extends RuntimeException {

    public FalhaAoRegistrarBoletoException(Throwable cause) {
        super("Falha tecnica ao registrar o boleto.", cause);
    }
}
