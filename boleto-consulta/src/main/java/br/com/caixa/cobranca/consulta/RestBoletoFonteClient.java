package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

public class RestBoletoFonteClient implements BoletoFonteClient {

    private final String fonte;
    private final String baseUrl;
    private final RestClient restClient;

    public RestBoletoFonteClient(String fonte, String baseUrl, RestClient.Builder builder) {
        this.fonte = fonte;
        this.baseUrl = baseUrl;
        this.restClient = builder.build();
    }

    @Override
    public BoletoConsulta buscar(UUID identificador) {
        try {
            BoletoConsulta boleto = restClient.get()
                    .uri(baseUrl + "/interno/boletos/{identificador}", identificador)
                    .retrieve()
                    .body(BoletoConsulta.class);
            if (boleto == null) {
                throw new FalhaFonteBoletoException(fonte, new IllegalStateException("Resposta vazia."));
            }
            return boleto;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new BoletoNaoEncontradoException();
            }
            throw new FalhaFonteBoletoException(fonte, ex);
        } catch (RestClientException ex) {
            throw new FalhaFonteBoletoException(fonte, ex);
        }
    }
}
