package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
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
        return persistencia.registrar(comando, cenario);
    }

    public enum Cenario {
        APROVAR, REJEITAR, INDISPONIVEL
    }
}
