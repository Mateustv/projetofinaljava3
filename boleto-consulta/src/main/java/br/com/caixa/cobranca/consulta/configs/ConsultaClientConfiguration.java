package br.com.caixa.cobranca.consulta.configs;

import br.com.caixa.cobranca.consulta.clients.BoletoFonteClient;
import br.com.caixa.cobranca.consulta.clients.RestBoletoFonteClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;

@Configuration
public class ConsultaClientConfiguration {

    @Bean
    @Qualifier("registroBoletoClient")
    BoletoFonteClient registroBoletoClient(
            RestClient.Builder builder,
            @Value("${integracoes.registro.base-url}") String baseUrl,
            @Value("${integracoes.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${integracoes.read-timeout-ms:3000}") int readTimeout) {
        return new RestBoletoFonteClient("registro", baseUrl, configurar(builder, connectTimeout, readTimeout));
    }

    @Bean
    @Qualifier("emissorBoletoClient")
    BoletoFonteClient emissorBoletoClient(
            RestClient.Builder builder,
            @Value("${integracoes.emissor.base-url}") String baseUrl,
            @Value("${integracoes.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${integracoes.read-timeout-ms:3000}") int readTimeout) {
        return new RestBoletoFonteClient("emissor", baseUrl, configurar(builder, connectTimeout, readTimeout));
    }

    public static RestClient.Builder configurar(RestClient.Builder builder, int connectTimeout, int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException("Timeouts HTTP devem ser positivos.");
        }
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder.clone().requestFactory(factory);
    }
}
