package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:simulada-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RegistradoraSimuladaTest {

    private final RegistrarBoletoCommand comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");

    @Autowired
    private RegistradoraSimuladaPersistence persistencia;

    @Test
    void aprovacaoGeraIdentificadorSimulado() {
        var resultado = new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR, persistencia).registrar(comando);
        assertEquals(StatusBoleto.REGISTRADO, resultado.status());
        assertEquals("SIM-" + comando.identificador(), resultado.identificador());
        assertNull(resultado.codigoRejeicao());
        assertEquals("NAO-PAGAVEL-SIM-LINHA-" + comando.identificador(), resultado.linhaDigitavel());
        assertEquals("NAO-PAGAVEL-SIM-BARRAS-" + comando.identificador(), resultado.codigoBarras());
    }

    @Test
    void rejeicaoDeNegocioNaoLancaFalhaTecnica() {
        var resultado = new RegistradoraSimulada(RegistradoraSimulada.Cenario.REJEITAR, persistencia).registrar(comando);
        assertEquals(StatusBoleto.REJEITADO, resultado.status());
        assertEquals("SIM-REJ-001", resultado.codigoRejeicao());
        assertNull(resultado.identificador());
        assertNull(resultado.dataHoraRegistro());
        assertNull(resultado.linhaDigitavel());
        assertNull(resultado.codigoBarras());
    }

    @Test
    void indisponibilidadeLancaFalhaTecnica() {
        assertThrows(FalhaAoRegistrarBoletoException.class,
                () -> new RegistradoraSimulada(RegistradoraSimulada.Cenario.INDISPONIVEL, persistencia).registrar(comando));
    }

    @Test
    void respostaPersistidaSobreviveATrocaDeCenarioENaoERecalculada() {
        var original = new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR, persistencia).registrar(comando);
        var recuperado = new RegistradoraSimulada(RegistradoraSimulada.Cenario.INDISPONIVEL, persistencia).registrar(comando);
        assertEquals(original, recuperado);
    }

    @Test
    void mesmaChaveComOutroConteudoConflitaNaFronteiraSimulada() {
        new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR, persistencia).registrar(comando);
        var alterado = new RegistrarBoletoCommand(comando.identificador(), comando.aceite(),
                RegistroComandoControllerTest.comando(comando.identificador(), "151.00").solicitacao());
        assertThrows(ComandoRegistroConflitanteException.class,
                () -> new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR, persistencia).registrar(alterado));
    }

    @Test
    void resultadoRegistradoSemDadosDePagamentoEInconsistente() {
        assertThrows(IllegalArgumentException.class, () -> new ResultadoRegistradora(
                StatusBoleto.REGISTRADO, "SIM", LocalDateTime.now(), null, null, null, null));
    }

    @Test
    void rejeicaoNaoPodeTrazerDadosDePagamento() {
        assertThrows(IllegalArgumentException.class, () -> new ResultadoRegistradora(
                StatusBoleto.REJEITADO, null, null, "SIM-REJ-001", "Rejeicao", "nao-expor", "nao-expor"));
    }

    @Test
    void chamadasConcorrentesComConteudoDiferenteNaoSobrescrevemOperacao() throws Exception {
        var alterado = new RegistrarBoletoCommand(comando.identificador(), comando.aceite(),
                RegistroComandoControllerTest.comando(comando.identificador(), "151.00").solicitacao());
        var inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tarefas = List.of(comando, alterado).stream()
                    .map(item -> executor.submit(() -> {
                        inicio.await();
                        return persistencia.registrar(item, RegistradoraSimulada.Cenario.APROVAR);
                    })).toList();
            inicio.countDown();
            int sucessos = 0;
            int conflitos = 0;
            for (var tarefa : tarefas) {
                try {
                    tarefa.get(10, TimeUnit.SECONDS);
                    sucessos++;
                } catch (ExecutionException ex) {
                    assertTrue(ex.getCause() instanceof ComandoRegistroConflitanteException
                            || ex.getCause() instanceof DataIntegrityViolationException);
                    conflitos++;
                }
            }
            assertEquals(1, sucessos);
            assertEquals(1, conflitos);
        } finally {
            executor.shutdownNow();
        }
    }
}
