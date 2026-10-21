package br.com.caixa.cobranca.http.demo;

import br.com.caixa.cobranca.http.Correlacao;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/**
 * Diagnostico local limitado por JVM; nunca e trilha de auditoria ou fonte de negocio.
 */
public final class Rastros {
    private static final int LIMITE = 2000;
    private static final ArrayDeque<Rastro> ITENS = new ArrayDeque<>();
    private static volatile String servico;

    private Rastros() {
    }

    public static synchronized void ativar(String nome) {
        ITENS.clear();
        servico = nome;
    }

    public static synchronized void desativar() {
        servico = null;
        ITENS.clear();
    }

    public static boolean ativo() {
        return servico != null;
    }

    public static synchronized void registrar(UUID boleto, UUID correlacao, String etapa, String detalhe) {
        if (servico == null) {
            return;
        }
        if (ITENS.size() == LIMITE) {
            ITENS.removeFirst();
        }
        ITENS.addLast(new Rastro(UUID.randomUUID(), Instant.now(), servico, boleto,
                correlacao, etapa, detalhe));
    }

    public static void registrar(UUID boleto, String etapa, String detalhe) {
        registrar(boleto, Correlacao.atualOu(null), etapa, detalhe);
    }

    public static void aposCommit(UUID boleto, UUID correlacao, String etapa, String detalhe) {
        if (!ativo()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Rastro de commit exige transacao ativa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                registrar(boleto, correlacao, etapa, detalhe);
            }
        });
    }

    public static synchronized List<Rastro> buscar(UUID boleto) {
        return ITENS.stream().filter(item -> boleto.equals(item.boleto())).toList();
    }

    public record Rastro(UUID id, Instant instante, String servico, UUID boleto,
                         UUID correlacao, String etapa, String detalhe) {
    }
}
