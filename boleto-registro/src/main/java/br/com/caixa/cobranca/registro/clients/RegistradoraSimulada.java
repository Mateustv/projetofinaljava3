package br.com.caixa.cobranca.registro.clients;

import br.com.caixa.cobranca.registro.dtos.ResultadoRegistradora;
import br.com.caixa.cobranca.registro.services.RegistradoraSimuladaPersistence;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.http.demo.Rastros;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class RegistradoraSimulada implements RegistradoraClient {

    private final Cenario cenario;
    private final RegistradoraSimuladaPersistence persistencia;

    public RegistradoraSimulada(@Value("${registro.simulacao.cenario:APROVAR}") Cenario cenario,
                                RegistradoraSimuladaPersistence persistencia) {
        this.cenario = cenario;
        this.persistencia = persistencia;
    }

    @Override
    public ResultadoRegistradora registrar(RegistrarBoletoCommand comando) {
        Cenario escolhido = cenario;
        if (Rastros.ativo()) {
            String controle = comando.solicitacao().numeroControleCliente();
            if (controle.startsWith("DEMO-A-")) {
                escolhido = Cenario.APROVAR;
            } else if (controle.startsWith("DEMO-R-")) {
                escolhido = Cenario.REJEITAR;
            } else if (controle.startsWith("DEMO-F-")) {
                escolhido = Cenario.INDISPONIVEL;
            }
        }
        Rastros.registrar(comando.identificador(), comando.correlationId(), "REGISTRADORA",
                "Chamada da simulacao local; cenario=" + escolhido);
        return persistencia.registrar(comando, escolhido);
    }

    public enum Cenario {
        APROVAR, REJEITAR, INDISPONIVEL
    }
}
