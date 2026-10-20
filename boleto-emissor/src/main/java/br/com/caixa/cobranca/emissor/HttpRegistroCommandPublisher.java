package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpRegistroCommandPublisher implements RegistroCommandPublisher {

    private final RestClient restClient;
    private final String registroUrl;

    public HttpRegistroCommandPublisher(RestClient.Builder builder,
                                        @Value("${integracao.registro.url:http://localhost:8082}") String registroUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.restClient = builder.requestFactory(factory).build();
        this.registroUrl = registroUrl;
    }

    @Override
    public void publicar(RegistrarBoletoCommand comando) {
        var resposta = restClient.post()
                .uri(registroUrl + "/interno/comandos/registro")
                .body(comando)
                .retrieve()
                .toBodilessEntity();
        if (!resposta.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Entrega nao confirmada pelo registro.");
        }
    }
}
