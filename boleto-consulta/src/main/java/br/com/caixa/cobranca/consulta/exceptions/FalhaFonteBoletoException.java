package br.com.caixa.cobranca.consulta.exceptions;

public class FalhaFonteBoletoException extends RuntimeException {

    public FalhaFonteBoletoException(String fonte, Throwable cause) {
        super("Falha tecnica ao consultar a fonte " + fonte + ".", cause);
    }
}
