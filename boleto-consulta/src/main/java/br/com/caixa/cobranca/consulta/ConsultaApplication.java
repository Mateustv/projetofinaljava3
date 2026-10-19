package br.com.caixa.cobranca.consulta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do bounded context CONSULTA (porta 8083). */
@SpringBootApplication
public class ConsultaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultaApplication.class, args);
    }
}
