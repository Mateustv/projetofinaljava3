package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class RegistradoraSimulada implements RegistradoraClient {

    private final Cenario cenario;

    public RegistradoraSimulada(@Value("${registro.simulacao.cenario:APROVAR}") Cenario cenario) {
        this.cenario = cenario;
    }

    @Override
    public ResultadoRegistradora registrar(RegistrarBoletoCommand comando) {
        return switch (cenario) {
            case APROVAR -> new ResultadoRegistradora(
                    StatusBoleto.REGISTRADO,
                    "SIM-" + comando.identificador(),
                    LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), null, null);
            case REJEITAR -> new ResultadoRegistradora(
                    StatusBoleto.REJEITADO, null, null,
                    "SIM-REJ-001", "Titulo recusado pela registradora simulada.");
            case INDISPONIVEL -> throw new FalhaAoRegistrarBoletoException(
                    new IllegalStateException("Registradora simulada indisponivel."));
        };
    }

    public enum Cenario {
        APROVAR, REJEITAR, INDISPONIVEL
    }
}
