package br.com.caixa.cobranca.http.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RastrosTest {
    @AfterEach
    void limpar() {
        Rastros.desativar();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void desligadoNaoGuardaNemExigeTransacao() {
        Rastros.desativar();
        UUID boleto = UUID.randomUUID();
        Rastros.registrar(boleto, "IGNORADO", "Teste");
        Rastros.aposCommit(boleto, null, "IGNORADO", "Teste");
        assertTrue(Rastros.buscar(boleto).isEmpty());
    }

    @Test
    void janelaELimitadaEFiltradaPorBoleto() {
        Rastros.ativar("teste");
        UUID primeiro = UUID.randomUUID(), outro = UUID.randomUUID();
        Rastros.registrar(outro, "PRIMEIRO", "Expira por capacidade");
        for (int i = 0; i < 2001; i++) {
            Rastros.registrar(primeiro, "ETAPA", Integer.toString(i));
        }
        assertTrue(Rastros.buscar(outro).isEmpty());
        assertEquals(2000, Rastros.buscar(primeiro).size());
        assertEquals("1", Rastros.buscar(primeiro).get(0).detalhe());
        Rastros.desativar();
        assertTrue(Rastros.buscar(primeiro).isEmpty());
    }

    @Test
    void commitNaoEAfirmadoAntesDoCommitNemAposRollback() {
        Rastros.ativar("teste");
        UUID boleto = UUID.randomUUID();
        assertThrows(IllegalStateException.class,
                () -> Rastros.aposCommit(boleto, null, "COMMIT", "Teste"));
        TransactionSynchronizationManager.initSynchronization();
        Rastros.aposCommit(boleto, null, "COMMIT", "Teste");
        assertTrue(Rastros.buscar(boleto).isEmpty());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        assertTrue(Rastros.buscar(boleto).isEmpty());
        TransactionSynchronizationManager.initSynchronization();
        Rastros.aposCommit(boleto, boleto, "COMMIT", "Teste");
        TransactionSynchronizationManager.getSynchronizations().forEach(
                org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertEquals(1, Rastros.buscar(boleto).size());
        assertEquals(boleto, Rastros.buscar(boleto).get(0).correlacao());
    }
}
