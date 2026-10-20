package br.com.caixa.cobranca.consulta.exceptions;

public class IdentificadorBoletoInvalidoException extends RuntimeException {

    public IdentificadorBoletoInvalidoException() {
        super("O identificador deve ser um UUID valido.");
    }
}
