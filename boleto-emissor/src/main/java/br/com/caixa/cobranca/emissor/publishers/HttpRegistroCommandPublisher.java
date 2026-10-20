package br.com.caixa.cobranca.emissor.publishers;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.http.Correlacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
                .header(Correlacao.HEADER, comando.correlationId().toString())
                .body(comando)
                .retrieve()
                .toBodilessEntity();
        if (!resposta.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Entrega nao confirmada pelo registro.");
        }
    }
}
