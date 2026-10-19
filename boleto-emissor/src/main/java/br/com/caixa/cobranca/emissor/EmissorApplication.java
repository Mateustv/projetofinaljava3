package br.com.caixa.cobranca.emissor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Ponto de entrada do bounded context EMISSAO (porta 8081). */
@SpringBootApplication
@EnableScheduling
public class EmissorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmissorApplication.class, args);
    }
}
