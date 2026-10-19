package br.com.caixa.cobranca.contracts;

/**
 * Estados do boleto (maquina de estados - requisitos/glossario-e-regras.md, secao 1).
 * AGUARDANDO_REGISTRO e transitorio; REGISTRADO e REJEITADO sao finais.
 */
public enum StatusBoleto {
    AGUARDANDO_REGISTRO, REGISTRADO, REJEITADO
}
