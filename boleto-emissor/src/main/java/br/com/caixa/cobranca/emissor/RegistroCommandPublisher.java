package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;

public interface RegistroCommandPublisher {

    void publicar(RegistrarBoletoCommand comando);
}
