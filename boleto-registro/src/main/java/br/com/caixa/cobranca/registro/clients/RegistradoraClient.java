package br.com.caixa.cobranca.registro.clients;

import br.com.caixa.cobranca.registro.dtos.ResultadoRegistradora;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;

public interface RegistradoraClient {

    ResultadoRegistradora registrar(RegistrarBoletoCommand comando);
}
