package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.http.Correlacao;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.UUID;

public class RestBoletoFonteClient implements BoletoFonteClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestBoletoFonteClient.class);
    private final String fonte;
    private final String baseUrl;
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public RestBoletoFonteClient(String fonte, String baseUrl, RestClient.Builder builder) {
        this(fonte, baseUrl, builder, novoCircuito(fonte));
    }

    RestBoletoFonteClient(String fonte, String baseUrl, RestClient.Builder builder, CircuitBreaker circuitBreaker) {
        this.fonte = fonte;
        this.baseUrl = baseUrl;
        this.restClient = builder.build();
        this.circuitBreaker = circuitBreaker;
    }

    static CircuitBreaker novoCircuito(String fonte) {
        var circuito = CircuitBreaker.of("consulta-" + fonte, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3).minimumNumberOfCalls(3).failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(ex -> !(ex instanceof BoletoNaoEncontradoException))
                .build());
        circuito.getEventPublisher().onStateTransition(event ->
                LOGGER.info("Circuito alterado. fonte={}, transicao={}", fonte, event.getStateTransition()));
        return circuito;
    }

    @Override
    public BoletoConsulta buscar(UUID identificador) {
        try {
            return circuitBreaker.executeSupplier(() -> buscarRemoto(identificador));
        } catch (CallNotPermittedException ex) {
            LOGGER.warn("Consulta bloqueada pelo circuito. fonte={}, correlation_id={}",
                    fonte, Correlacao.atualOu(null));
            throw new FalhaFonteBoletoException(fonte, ex);
        }
    }

    private BoletoConsulta buscarRemoto(UUID identificador) {
        try {
            BoletoConsulta boleto = restClient.get()
                    .uri(baseUrl + "/interno/boletos/{identificador}", identificador)
                    .header(Correlacao.HEADER, Correlacao.atualOu(UUID.randomUUID()).toString())
                    .retrieve()
                    .onStatus(status -> status.is3xxRedirection(), (request, response) -> {
                        throw new RestClientException("Redirecionamento de fonte nao permitido.");
                    })
                    .body(BoletoConsulta.class);
            BoletoFonteClient.validarResposta(boleto, identificador, fonte);
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
