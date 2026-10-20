package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistradoraSimuladaTest {

    private final RegistrarBoletoCommand comando = new RegistrarBoletoCommand(UUID.randomUUID(), null, null);

    @Test
    void aprovacaoGeraIdentificadorSimulado() {
        var resultado = new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR).registrar(comando);
        assertEquals(StatusBoleto.REGISTRADO, resultado.status());
        assertEquals("SIM-" + comando.identificador(), resultado.identificador());
        assertNull(resultado.codigoRejeicao());
    }

    @Test
    void rejeicaoDeNegocioNaoLancaFalhaTecnica() {
        var resultado = new RegistradoraSimulada(RegistradoraSimulada.Cenario.REJEITAR).registrar(comando);
        assertEquals(StatusBoleto.REJEITADO, resultado.status());
        assertEquals("SIM-REJ-001", resultado.codigoRejeicao());
        assertNull(resultado.identificador());
        assertNull(resultado.dataHoraRegistro());
    }

    @Test
    void indisponibilidadeLancaFalhaTecnica() {
        assertThrows(FalhaAoRegistrarBoletoException.class,
                () -> new RegistradoraSimulada(RegistradoraSimulada.Cenario.INDISPONIVEL).registrar(comando));
    }
}
