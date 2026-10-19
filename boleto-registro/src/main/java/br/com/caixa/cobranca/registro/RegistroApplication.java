package br.com.caixa.cobranca.registro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do bounded context REGISTRO (porta 8082). */
@SpringBootApplication
public class RegistroApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistroApplication.class, args);
    }
}
