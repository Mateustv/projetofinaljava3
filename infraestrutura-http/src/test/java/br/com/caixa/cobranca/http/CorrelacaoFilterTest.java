package br.com.caixa.cobranca.http;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CorrelacaoFilterTest {

    private final CorrelacaoFilter filter = new CorrelacaoFilter();

    @AfterEach
    void semVazamento() {
        assertNull(Correlacao.atualOu(null));
        assertNull(MDC.get("correlation_id"));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202, 400, 404, 409, 503})
    void preservaHeaderValidadoInclusiveEmErros(int status) throws Exception {
        UUID id = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.addHeader(Correlacao.HEADER, id.toString());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals(id, Correlacao.atualOu(null));
            assertEquals(id.toString(), MDC.get("correlation_id"));
            response.setStatus(status);
        });
        assertEquals(id.toString(), response.getHeader(Correlacao.HEADER));
        assertEquals(status, response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalido", "1-1-4-8-1", "11111111-1111-1111-8111-111111111111",
            "11111111-1111-4111-0111-111111111111", " 11111111-1111-4111-8111-111111111111",
            "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", "11111111-1111-4111-8111-111111111111,22222222"})
    void invalidoNaoChegaAoDominioNemRefleteEntrada(String valor) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(Correlacao.HEADER, valor);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Nao deve executar dominio."));
        assertEquals(400, response.getStatus());
        assertEquals(4, UUID.fromString(response.getHeader(Correlacao.HEADER)).version());
        assertTrue(response.getContentAsString().contains("CORRELACAO_INVALIDA"));
        assertTrue(response.getContentAsString().contains(response.getHeader(Correlacao.HEADER)));
    }

    @Test
    void headersDuplicadosSaoRecusados() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(Correlacao.HEADER, UUID.randomUUID().toString());
        request.addHeader(Correlacao.HEADER, UUID.randomUUID().toString());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail());
        assertEquals(400, response.getStatus());
    }

    @Test
    void ausenteGeraNovoIdPorRequisicaoNaMesmaThread() throws Exception {
        String anterior = null;
        for (int i = 0; i < 2; i++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(), response, (req, res) ->
                    assertEquals(response.getHeader(Correlacao.HEADER), Correlacao.atualOu(null).toString()));
            String atual = response.getHeader(Correlacao.HEADER);
            assertEquals(4, UUID.fromString(atual).version());
            assertNotEquals(anterior, atual);
            anterior = atual;
        }
    }

    @Test
    void excecaoLimpaThreadEMdc() {
        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                    throw new ServletException("Falha simulada.");
                }));
    }

    @Test
    void escopoAninhadoRestauraContextoAnterior() {
        UUID original = UUID.randomUUID();
        try (var externo = Correlacao.abrir(original)) {
            try (var interno = Correlacao.abrir(UUID.randomUUID())) {
                assertNotEquals(original, Correlacao.atualOu(null));
            }
            assertEquals(original, Correlacao.atualOu(null));
            assertEquals(original.toString(), MDC.get("correlation_id"));
        }
    }

    @Test
    void threadsNaoCompartilhamCorrelacao() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (var escopo = Correlacao.abrir(UUID.randomUUID())) {
            assertNull(executor.submit(() -> Correlacao.atualOu(null)).get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
