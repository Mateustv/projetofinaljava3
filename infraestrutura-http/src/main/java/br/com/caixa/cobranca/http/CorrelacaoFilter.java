package br.com.caixa.cobranca.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

public class CorrelacaoFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelacaoFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(Correlacao.HEADER));
        UUID id = UUID.randomUUID();
        boolean invalido = false;
        if (!headers.isEmpty()) {
            String valor = headers.get(0);
            try {
                UUID recebido = UUID.fromString(valor);
                invalido = headers.size() != 1 || !recebido.toString().equals(valor)
                        || recebido.version() != 4 || recebido.variant() != 2;
                if (!invalido) {
                    id = recebido;
                }
            } catch (IllegalArgumentException ex) {
                invalido = true;
            }
        }
        response.setHeader(Correlacao.HEADER, id.toString());
        request.setAttribute(Correlacao.HEADER, id);
        try (var escopo = Correlacao.abrir(id)) {
            if (invalido) {
                LOGGER.warn("Requisicao recusada. correlation_id={}, tipo=CORRELACAO_INVALIDA", id);
                response.setStatus(400);
                response.setContentType("application/json");
                response.getWriter().write("{\"codigo\":\"CORRELACAO_INVALIDA\",\"correlation_id\":\"" + id + "\"}");
                return;
            }
            chain.doFilter(request, response);
            LOGGER.info("Requisicao concluida. correlation_id={}, metodo={}, status={}",
                    id, request.getMethod(), response.getStatus());
        }
    }
}
