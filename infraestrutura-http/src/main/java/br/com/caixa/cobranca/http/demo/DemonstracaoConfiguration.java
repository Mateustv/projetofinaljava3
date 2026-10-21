package br.com.caixa.cobranca.http.demo;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demonstracao")
public class DemonstracaoConfiguration implements DisposableBean {
    public DemonstracaoConfiguration(@Value("${spring.application.name}") String nome) {
        Rastros.ativar(nome);
    }

    @Override
    public void destroy() {
        Rastros.desativar();
    }
}
