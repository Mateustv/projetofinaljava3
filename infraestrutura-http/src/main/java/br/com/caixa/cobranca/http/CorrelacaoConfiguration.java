package br.com.caixa.cobranca.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
public class CorrelacaoConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CorrelacaoFilter correlacaoFilter() {
        return new CorrelacaoFilter();
    }
}
