package br.com.caixa.cobranca.http;

import org.slf4j.MDC;

import java.util.UUID;

public final class Correlacao {

    public static final String HEADER = "X-Correlation-ID";
    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();

    private Correlacao() {
    }

    public static UUID atualOu(UUID padrao) {
        UUID atual = ATUAL.get();
        return atual == null ? padrao : atual;
    }

    public static Escopo abrir(UUID id) {
        return new Escopo(id);
    }

    public static final class Escopo implements AutoCloseable {
        private final UUID anterior = ATUAL.get();
        private final String mdcAnterior = MDC.get("correlation_id");

        private Escopo(UUID id) {
            ATUAL.set(id);
            MDC.put("correlation_id", id.toString());
        }

        @Override
        public void close() {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
            if (mdcAnterior == null) {
                MDC.remove("correlation_id");
            } else {
                MDC.put("correlation_id", mdcAnterior);
            }
        }
    }
}
