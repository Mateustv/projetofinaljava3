package br.com.caixa.cobranca.emissor.exceptions;

import br.com.caixa.cobranca.contracts.CampoInvalido;

import java.util.List;

/** Lancada quando a emissao tem campos invalidos; vira HTTP 400 com TODOS os erros. */
public class EmissaoInvalidaException extends RuntimeException {

    private final transient List<CampoInvalido> erros;

    public EmissaoInvalidaException(List<CampoInvalido> erros) {
        super("Emissao invalida: " + erros.size() + " campo(s)");
        this.erros = List.copyOf(erros);
    }

    public List<CampoInvalido> getErros() {
        return erros;
    }
}
