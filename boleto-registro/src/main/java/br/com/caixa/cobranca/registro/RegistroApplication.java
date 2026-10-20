package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.eventos.EventosOutboxConfiguration;
import br.com.caixa.cobranca.http.CorrelacaoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do bounded context REGISTRO (porta 8082). */
@SpringBootApplication(scanBasePackageClasses = {
        RegistroApplication.class, EventosOutboxConfiguration.class, CorrelacaoConfiguration.class})
public class RegistroApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistroApplication.class, args);
    }
}
