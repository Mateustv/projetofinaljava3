package br.com.caixa.cobranca.registro;

public class FalhaAoRegistrarBoletoException extends RuntimeException {

    public FalhaAoRegistrarBoletoException(Throwable cause) {
        super("Falha tecnica ao registrar o boleto.", cause);
    }
}
