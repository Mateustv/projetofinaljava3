package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class RegistradoraSimulada implements RegistradoraClient {

    @Override
    public ResultadoRegistradora registrar(RegistrarBoletoCommand comando) {
        return new ResultadoRegistradora(
                "SIM-" + comando.identificador(),
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        );
    }
}
