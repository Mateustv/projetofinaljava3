package br.com.caixa.cobranca.emissor.publishers;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;

public interface RegistroCommandPublisher {

    void publicar(RegistrarBoletoCommand comando);
}
