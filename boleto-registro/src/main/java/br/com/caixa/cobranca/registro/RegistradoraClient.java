package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;

public interface RegistradoraClient {

    ResultadoRegistradora registrar(RegistrarBoletoCommand comando);
}
