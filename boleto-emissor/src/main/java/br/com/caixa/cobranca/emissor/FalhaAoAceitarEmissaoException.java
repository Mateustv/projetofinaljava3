package br.com.caixa.cobranca.emissor;

public class FalhaAoAceitarEmissaoException extends RuntimeException {

    public FalhaAoAceitarEmissaoException(Throwable cause) {
        super("Nao foi possivel persistir o aceite da emissao.", cause);
    }
}
