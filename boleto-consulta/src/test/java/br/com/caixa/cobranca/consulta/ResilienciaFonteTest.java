package br.com.caixa.cobranca.consulta;

import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import br.com.caixa.cobranca.http.Correlacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.internal.CircuitBreakerStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResilienciaFonteTest {

    private final UUID id = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void aberturaRespeitaDezSegundosESondaUnicaAntesDeRecuperar() throws Exception {
        var instante = new AtomicReference<>(Instant.parse("2026-10-20T15:00:00Z"));
        var clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> instante.get());
        org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var config = RestBoletoFonteClient.novoCircuito("tempo").getCircuitBreakerConfig();
        var circuito = new CircuitBreakerStateMachine("tempo", config, clock);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RestBoletoFonteClient("registro", "http://registro", builder, circuito);
        server.expect(times(3), requestTo(url())).andRespond(withServerError());
        for (int i = 0; i < 3; i++) {
            assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        }
        instante.set(instante.get().plusSeconds(9));
        assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        assertEquals(CircuitBreaker.State.OPEN, circuito.getState());
        server.verify();
        server.reset();
        instante.set(instante.get().plusSeconds(2));
        assertTrue(circuito.tryAcquirePermission());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuito.getState());
        assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        circuito.onError(1, TimeUnit.MILLISECONDS, new IllegalStateException("Sonda falhou."));
        assertEquals(CircuitBreaker.State.OPEN, circuito.getState());
        instante.set(instante.get().plusSeconds(11));
        server.expect(requestTo(url())).andRespond(withSuccess(json(StatusBoleto.REGISTRADO), MediaType.APPLICATION_JSON));
        assertEquals(StatusBoleto.REGISTRADO, client.buscar(id).status());
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
        server.verify();
    }

    @Test
    void tresFalhasAbremCircuitoSemRetryEFalhaDeSondaReabreDepoisRecupera() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var circuito = RestBoletoFonteClient.novoCircuito("teste");
        var client = new RestBoletoFonteClient("registro", "http://registro", builder, circuito);
        server.expect(times(3), requestTo(url())).andRespond(withServerError());
        for (int i = 0; i < 3; i++) {
            assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        }
        assertEquals(CircuitBreaker.State.OPEN, circuito.getState());
        for (int i = 0; i < 5; i++) {
            assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        }
        assertEquals(5, circuito.getMetrics().getNumberOfNotPermittedCalls());
        server.verify();
        server.reset();
        circuito.transitionToHalfOpenState();
        server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        assertEquals(CircuitBreaker.State.OPEN, circuito.getState());
        server.verify();
        server.reset();
        circuito.transitionToHalfOpenState();
        server.expect(requestTo(url())).andRespond(withSuccess(json(StatusBoleto.REGISTRADO), MediaType.APPLICATION_JSON));
        assertEquals(StatusBoleto.REGISTRADO, client.buscar(id).status());
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
        server.verify();
        assertEquals(10000, circuito.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1));
        assertEquals(1, circuito.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
    }

    @Test
    void ausenciaERejeicaoDeNegocioContamComoDisponibilidade() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var circuito = RestBoletoFonteClient.novoCircuito("negocio");
        var client = new RestBoletoFonteClient("registro", "http://registro", builder, circuito);
        server.expect(times(5), requestTo(url())).andRespond(withResourceNotFound());
        for (int i = 0; i < 5; i++) {
            assertThrows(BoletoNaoEncontradoException.class, () -> client.buscar(id));
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
        assertEquals(0, circuito.getMetrics().getNumberOfFailedCalls());
        server.verify();
        server.reset();
        server.expect(times(3), requestTo(url()))
                .andRespond(withSuccess(json(StatusBoleto.REJEITADO), MediaType.APPLICATION_JSON));
        for (int i = 0; i < 3; i++) {
            assertEquals(StatusBoleto.REJEITADO, client.buscar(id).status());
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
        assertEquals(0, circuito.getMetrics().getNumberOfFailedCalls());
        server.verify();
        server.reset();
        circuito.transitionToOpenState();
        circuito.transitionToHalfOpenState();
        server.expect(requestTo(url())).andRespond(withResourceNotFound());
        assertThrows(BoletoNaoEncontradoException.class, () -> client.buscar(id));
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
        server.verify();
    }

    @Test
    void circuitoAbertoNaoAcionaFallbackNaoCacheiaFalhaEPreservaCacheHit() throws Exception {
        var builderRegistro = RestClient.builder();
        var serverRegistro = MockRestServiceServer.bindTo(builderRegistro).build();
        var builderEmissor = RestClient.builder();
        var serverEmissor = MockRestServiceServer.bindTo(builderEmissor).build();
        var circuito = RestBoletoFonteClient.novoCircuito("registro");
        var registro = new RestBoletoFonteClient("registro", "http://registro", builderRegistro, circuito);
        var emissor = new RestBoletoFonteClient("emissor", "http://emissor", builderEmissor);
        var caches = new ConcurrentMapCacheManager(ConsultaCacheConfiguration.CACHE_BOLETOS);
        var consulta = new ConsultaBoletoService(registro, emissor, caches);
        serverRegistro.expect(times(3), requestTo(url())).andRespond(withServerError());
        for (int i = 0; i < 4; i++) {
            assertThrows(FalhaFonteBoletoException.class, () -> consulta.consultar(id));
            assertNull(caches.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).get(id));
        }
        assertEquals(CircuitBreaker.State.OPEN, circuito.getState());
        UUID emCache = UUID.randomUUID();
        var boleto = new BoletoConsulta(emCache, StatusBoleto.REGISTRADO, LocalDateTime.now(),
                null, null, null, null, null, null);
        caches.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).put(emCache, boleto);
        assertEquals(boleto, consulta.consultar(emCache));
        serverRegistro.verify();
        serverEmissor.verify();
    }

    @Test
    void fallbackApos404PropagaMesmaCorrelacaoNasDuasFontes() throws Exception {
        UUID correlation = UUID.randomUUID();
        var builderRegistro = RestClient.builder();
        var serverRegistro = MockRestServiceServer.bindTo(builderRegistro).build();
        var builderEmissor = RestClient.builder();
        var serverEmissor = MockRestServiceServer.bindTo(builderEmissor).build();
        serverRegistro.expect(requestTo(url())).andExpect(header(Correlacao.HEADER, correlation.toString()))
                .andRespond(withResourceNotFound());
        serverEmissor.expect(requestTo("http://emissor/interno/boletos/" + id))
                .andExpect(header(Correlacao.HEADER, correlation.toString()))
                .andRespond(withSuccess(json(StatusBoleto.AGUARDANDO_REGISTRO), MediaType.APPLICATION_JSON));
        var consulta = new ConsultaBoletoService(
                new RestBoletoFonteClient("registro", "http://registro", builderRegistro),
                new RestBoletoFonteClient("emissor", "http://emissor", builderEmissor),
                new ConcurrentMapCacheManager(ConsultaCacheConfiguration.CACHE_BOLETOS));
        try (var escopo = Correlacao.abrir(correlation)) {
            assertEquals(StatusBoleto.AGUARDANDO_REGISTRO, consulta.consultar(id).status());
        }
        serverRegistro.verify();
        serverEmissor.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 408, 429, 500, 503})
    void erroNaoVira404NemTemTentativaAdicional(int status) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.valueOf(status)));
        var client = new RestBoletoFonteClient("registro", "http://registro", builder);
        assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{invalido", "{}", "{\"status\":\"DESCONHECIDO\"}"})
    void respostaInconsistenteNaoEhSucessoDoCircuito(String json) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var circuito = RestBoletoFonteClient.novoCircuito("contrato");
        var client = new RestBoletoFonteClient("registro", "http://registro", builder, circuito);
        server.expect(requestTo(url())).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
        assertEquals(1, circuito.getMetrics().getNumberOfFailedCalls());
        server.verify();
    }

    @Test
    void correlacaoSegueNaChamadaInterna() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var correlation = UUID.randomUUID();
        server.expect(requestTo(url())).andExpect(header(Correlacao.HEADER, correlation.toString()))
                .andRespond(withSuccess(json(StatusBoleto.REGISTRADO), MediaType.APPLICATION_JSON));
        try (var escopo = Correlacao.abrir(correlation)) {
            assertEquals(id, new RestBoletoFonteClient("registro", "http://registro", builder).buscar(id).identificador());
        }
        assertNull(Correlacao.atualOu(null));
        server.verify();
    }

    @Test
    void fontesTemCircuitosIndependentes() {
        var registro = RestBoletoFonteClient.novoCircuito("registro");
        var emissor = RestBoletoFonteClient.novoCircuito("emissor");
        registro.transitionToOpenState();
        assertEquals(CircuitBreaker.State.CLOSED, emissor.getState());
    }

    @Test
    void timeoutDeLeituraRealTemLimiteENaoRepeteRequisicao() throws Exception {
        var chamadas = new AtomicInteger();
        var recebida = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            chamadas.incrementAndGet();
            recebida.countDown();
            try {
                liberar.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var builder = ConsultaClientConfiguration.configurar(RestClient.builder(), 200, 150);
            var client = new RestBoletoFonteClient("registro", "http://127.0.0.1:" + server.getAddress().getPort(), builder);
            long inicio = System.nanoTime();
            var erro = assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
            long tempo = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            assertTrue(tempo < 2000, "Timeout levou " + tempo + " ms");
            assertInstanceOf(org.springframework.web.client.ResourceAccessException.class, erro.getCause());
            assertInstanceOf(java.net.SocketTimeoutException.class, erro.getCause().getCause());
            assertTrue(recebida.await(5, TimeUnit.SECONDS));
            assertEquals(1, chamadas.get());
        } finally {
            liberar.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void redirecionamentoNaoChamaDestinoNovo() throws Exception {
        var chamadas = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            chamadas.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/outro");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new RestBoletoFonteClient("registro", "http://127.0.0.1:" + server.getAddress().getPort(),
                    ConsultaClientConfiguration.configurar(RestClient.builder(), 200, 200));
            assertThrows(FalhaFonteBoletoException.class, () -> client.buscar(id));
            assertEquals(1, chamadas.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutZeroOuNegativoFalhaNaConfiguracao() {
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaClientConfiguration.configurar(RestClient.builder(), 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaClientConfiguration.configurar(RestClient.builder(), 100, -1));
    }

    private String url() {
        return "http://registro/interno/boletos/" + id;
    }

    private String json(StatusBoleto status) throws Exception {
        return mapper.writeValueAsString(new BoletoConsulta(id, status, LocalDateTime.now(),
                null, null, null, null, null, null));
    }
}
