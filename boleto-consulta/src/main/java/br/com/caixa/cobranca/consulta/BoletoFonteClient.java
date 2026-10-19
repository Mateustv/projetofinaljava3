package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;

import java.util.UUID;

public interface BoletoFonteClient {

    BoletoConsulta buscar(UUID identificador);
}
