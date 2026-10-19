package br.com.caixa.cobranca.consulta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConsultaBoletoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(name = "registroBoletoClient")
    private BoletoFonteClient registro;

    @MockBean(name = "emissorBoletoClient")
    private BoletoFonteClient emissor;

    @BeforeEach
    void limparCache() {
        cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS).clear();
    }

    @Test
    void fallbackNoEmissorRetornaAguardandoECacheiaResultado() throws Exception {
        UUID id = UUID.randomUUID();
        BoletoConsulta aceite = new BoletoConsulta(
                id, StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.now(),
                "nao-expor", "nao-expor", "nao-expor", LocalDateTime.now(), "nao-expor", "nao-expor"
        );
        when(registro.buscar(id)).thenThrow(new BoletoNaoEncontradoException());
        when(emissor.buscar(id)).thenReturn(aceite);

        mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_REGISTRO"))
                .andExpect(jsonPath("$.linha_digitavel").doesNotExist())
                .andExpect(jsonPath("$.codigo_barras").doesNotExist())
                .andExpect(jsonPath("$.motivo_rejeicao").doesNotExist());
        mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isOk());

        verify(registro, times(1)).buscar(id);
        verify(emissor, times(1)).buscar(id);
    }

    @Test
    void registroEConsultadoAntesDoFallbackEEstadoFinalSoExpõeSeusDados() throws Exception {
        UUID id = UUID.randomUUID();
        when(registro.buscar(id)).thenReturn(new BoletoConsulta(
                id, StatusBoleto.REJEITADO, LocalDateTime.now(),
                "nao-expor", "nao-expor", "nao-expor", LocalDateTime.now(), "REJ-001", "Documento inválido"
        ));

        mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"))
                .andExpect(jsonPath("$.codigo_rejeicao").value("REJ-001"))
                .andExpect(jsonPath("$.motivo_rejeicao").value("Documento inválido"))
                .andExpect(jsonPath("$.linha_digitavel").doesNotExist())
                .andExpect(jsonPath("$.codigo_barras").doesNotExist());

        verify(emissor, never()).buscar(id);
    }

    @Test
    void resultadoRegistradoExpõeDadosDePagamento() throws Exception {
        UUID id = UUID.randomUUID();
        when(registro.buscar(id)).thenReturn(new BoletoConsulta(
                id, StatusBoleto.REGISTRADO, LocalDateTime.now(),
                "12345.67890 12345.678901 12345.678901 1 12345678901234",
                "12345678901234567890123456789012345678901234",
                "REG-001", LocalDateTime.now(), "nao-expor", "nao-expor"
        ));

        mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRADO"))
                .andExpect(jsonPath("$.linha_digitavel").exists())
                .andExpect(jsonPath("$.codigo_barras").exists())
                .andExpect(jsonPath("$.codigo_rejeicao").doesNotExist());
    }

    @Test
    void naoCacheia404EProcuraNasDuasFontesEmCadaConsulta() throws Exception {
        UUID id = UUID.randomUUID();
        when(registro.buscar(id)).thenThrow(new BoletoNaoEncontradoException());
        when(emissor.buscar(id)).thenThrow(new BoletoNaoEncontradoException());

        mvc.perform(get("/boletos/{identificador}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/boletos/{identificador}", id)).andExpect(status().isNotFound());

        verify(registro, times(2)).buscar(id);
        verify(emissor, times(2)).buscar(id);
    }

    @Test
    void falhaTecnicaNaFonteRetorna503ComCorrelationIdEPrecoceFallback() throws Exception {
        UUID id = UUID.randomUUID();
        when(registro.buscar(id)).thenThrow(new FalhaFonteBoletoException("registro", new RuntimeException()));

        MvcResult response = mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("FALHA_NA_CONSULTA"))
                .andReturn();
        MvcResult repeticao = mvc.perform(get("/boletos/{identificador}", id))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        String correlationId = objectMapper.readTree(response.getResponse().getContentAsString())
                .get("correlation_id").asText();
        assertEquals(correlationId, response.getResponse().getHeader("X-Correlation-ID"));
        assertNotNull(repeticao.getResponse().getHeader("X-Correlation-ID"));
        verify(registro, times(2)).buscar(id);
        verify(emissor, never()).buscar(id);
    }

    @Test
    void identificadorInvalidoRetorna400() throws Exception {
        mvc.perform(get("/boletos/{identificador}", "nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDENTIFICADOR_INVALIDO"));
    }

    @Test
    void cacheUsaTtlCurtoParaEstadoTransitorioELongoParaEstadoFinal() {
        assertEquals(ConsultaCacheConfiguration.TTL_AGUARDANDO,
                ConsultaCacheConfiguration.ttl(new BoletoConsulta(
                        UUID.randomUUID(), StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.now(),
                        null, null, null, null, null, null
                )));
        assertEquals(ConsultaCacheConfiguration.TTL_FINAL,
                ConsultaCacheConfiguration.ttl(new BoletoConsulta(
                        UUID.randomUUID(), StatusBoleto.REGISTRADO, LocalDateTime.now(),
                        null, null, null, LocalDateTime.now(), null, null
                )));
        assertEquals(ConsultaCacheConfiguration.TTL_FINAL,
                ConsultaCacheConfiguration.ttl(new BoletoConsulta(
                        UUID.randomUUID(), StatusBoleto.REJEITADO, LocalDateTime.now(),
                        null, null, null, null, "REJ-001", "Motivo"
                )));
    }

    @Test
    void cacheCaffeineAplicaExpiracaoVariavelPorEstado() {
        CaffeineCache springCache = assertInstanceOf(CaffeineCache.class,
                cacheManager.getCache(ConsultaCacheConfiguration.CACHE_BOLETOS));
        Cache<Object, Object> cache = springCache.getNativeCache();
        UUID aguardandoId = UUID.randomUUID();
        UUID registradoId = UUID.randomUUID();
        cache.put(aguardandoId, new BoletoConsulta(
                aguardandoId, StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.now(),
                null, null, null, null, null, null
        ));
        cache.put(registradoId, new BoletoConsulta(
                registradoId, StatusBoleto.REGISTRADO, LocalDateTime.now(),
                "linha", "barras", "registro", LocalDateTime.now(), null, null
        ));

        long ttlAguardando = cache.policy().expireVariably().orElseThrow()
                .getExpiresAfter(aguardandoId, TimeUnit.SECONDS).orElseThrow();
        long ttlRegistrado = cache.policy().expireVariably().orElseThrow()
                .getExpiresAfter(registradoId, TimeUnit.SECONDS).orElseThrow();
        assertTrue(ttlAguardando >= 29 && ttlAguardando <= 30);
        assertTrue(ttlRegistrado >= 599 && ttlRegistrado <= 600);
    }
}
