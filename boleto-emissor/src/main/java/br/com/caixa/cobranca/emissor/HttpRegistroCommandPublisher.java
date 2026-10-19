package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpRegistroCommandPublisher implements RegistroCommandPublisher {

    private final RestClient restClient;
    private final String registroUrl;

    public HttpRegistroCommandPublisher(RestClient.Builder builder,
                                        @Value("${integracao.registro.url:http://localhost:8082}") String registroUrl) {
        this.restClient = builder.build();
        this.registroUrl = registroUrl;
    }

    @Override
    public void publicar(RegistrarBoletoCommand comando) {
        restClient.post()
                .uri(registroUrl + "/interno/comandos/registro")
                .body(comando)
                .retrieve()
                .toBodilessEntity();
    }
}
