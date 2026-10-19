package br.com.caixa.cobranca.consulta;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ConsultaClientConfiguration {

    @Bean
    @Qualifier("registroBoletoClient")
    BoletoFonteClient registroBoletoClient(
            RestClient.Builder builder,
            @Value("${integracoes.registro.base-url}") String baseUrl) {
        return new RestBoletoFonteClient("registro", baseUrl, builder);
    }

    @Bean
    @Qualifier("emissorBoletoClient")
    BoletoFonteClient emissorBoletoClient(
            RestClient.Builder builder,
            @Value("${integracoes.emissor.base-url}") String baseUrl) {
        return new RestBoletoFonteClient("emissor", baseUrl, builder);
    }
}
