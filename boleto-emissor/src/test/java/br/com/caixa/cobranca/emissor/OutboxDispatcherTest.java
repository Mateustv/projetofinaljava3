package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.REQUEST_TIMEOUT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@ExtendWith(OutputCaptureExtension.class)
class OutboxDispatcherTest {

    private final ComandoRegistroOutboxRepository repository = mock(ComandoRegistroOutboxRepository.class);
    private final RegistroCommandPublisher publisher = mock(RegistroCommandPublisher.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Instant inicio = Instant.parse("2026-10-20T12:00:00Z");
    private final LocalDateTime agora = LocalDateTime.ofInstant(inicio, ZoneOffset.UTC);

    @Test
    void entregaComandoEMarcaOutboxComoEntregue() throws Exception {
        RegistrarBoletoCommand comando = comando();
        ComandoRegistroOutboxEntity outbox = outbox(comando);
        elegiveis(outbox);

        dispatcher(0).despacharPendentes();

        verify(publisher).publicar(comando);
        verify(repository).save(outbox);
        assertEquals(agora, outbox.getEntregueEm());
        assertNull(outbox.getComandoJson());
        assertNull(outbox.getFalhaDefinitivaEm());
        assertEquals(1, outbox.getTentativas());
        verify(repository).buscarElegiveis(agora, PageRequest.of(0, 20));
    }

    @Test
    void falhaTemporariaAgendaUmSegundoDepoisCincoEIsolaNaTerceiraTentativa(CapturedOutput logs) throws Exception {
        RegistrarBoletoCommand comando = comando();
        ComandoRegistroOutboxEntity outbox = outbox(comando);
        elegiveis(outbox);
        doThrow(new ResourceAccessException("DADO_SENSIVEL_SIMULADO"))
                .when(publisher).publicar(comando);

        dispatcher(0).despacharPendentes();
        assertEquals(agora.plusSeconds(1), outbox.getProximaTentativaEm());
        assertNull(outbox.getFalhaDefinitivaEm());
        dispatcher(1).despacharPendentes();
        assertEquals(agora.plusSeconds(6), outbox.getProximaTentativaEm());
        dispatcher(6).despacharPendentes();

        assertNull(outbox.getEntregueEm());
        assertNull(outbox.getProximaTentativaEm());
        assertEquals(agora.plusSeconds(6), outbox.getFalhaDefinitivaEm());
        assertNotNull(outbox.getComandoJson());
        assertEquals(3, outbox.getTentativas());
        assertEquals("ResourceAccessException", outbox.getUltimoErro());
        verify(publisher, times(3)).publicar(comando);
        assertTrue(logs.getAll().contains(comando.identificador().toString()));
        assertTrue(logs.getAll().contains("tentativa=3"));
        assertFalse(logs.getAll().contains("DADO_SENSIVEL_SIMULADO"));
    }

    @Test
    void recuperacaoAntesDoLimiteConfirmaEntregaELimpaErroEPayload() throws Exception {
        RegistrarBoletoCommand comando = comando();
        ComandoRegistroOutboxEntity outbox = outbox(comando);
        elegiveis(outbox);
        doThrow(HttpServerErrorException.create(SERVICE_UNAVAILABLE, "erro", HttpHeaders.EMPTY, null, null))
                .doNothing().when(publisher).publicar(comando);

        dispatcher(0).despacharPendentes();
        dispatcher(1).despacharPendentes();

        assertEquals(2, outbox.getTentativas());
        assertEquals(agora.plusSeconds(1), outbox.getEntregueEm());
        assertNull(outbox.getUltimoErro());
        assertNull(outbox.getProximaTentativaEm());
        assertNull(outbox.getFalhaDefinitivaEm());
        assertNull(outbox.getComandoJson());
    }

    @Test
    void erro400E409SaoPermanentesEMensagemBoaSegueNoMesmoLote() throws Exception {
        RegistrarBoletoCommand invalido = comando();
        RegistrarBoletoCommand conflitante = comando();
        RegistrarBoletoCommand bom = comando();
        ComandoRegistroOutboxEntity primeiro = outbox(invalido);
        ComandoRegistroOutboxEntity segundo = outbox(conflitante);
        ComandoRegistroOutboxEntity terceiro = outbox(bom);
        elegiveis(primeiro, segundo, terceiro);
        doThrow(HttpClientErrorException.create(BAD_REQUEST, "erro", HttpHeaders.EMPTY, null, null))
                .when(publisher).publicar(invalido);
        doThrow(HttpClientErrorException.create(CONFLICT, "erro", HttpHeaders.EMPTY, null, null))
                .when(publisher).publicar(conflitante);

        dispatcher(0).despacharPendentes();

        assertEquals(agora, primeiro.getFalhaDefinitivaEm());
        assertEquals("HTTP_400", primeiro.getUltimoErro());
        assertEquals(1, primeiro.getTentativas());
        assertEquals(agora, segundo.getFalhaDefinitivaEm());
        assertEquals("HTTP_409", segundo.getUltimoErro());
        assertNotNull(terceiro.getEntregueEm());
        verify(publisher).publicar(bom);
    }

    @Test
    void timeoutELimiteDeRequisicoesHttpPermitemRetry() throws Exception {
        RegistrarBoletoCommand timeout = comando();
        RegistrarBoletoCommand limite = comando();
        ComandoRegistroOutboxEntity primeiro = outbox(timeout);
        ComandoRegistroOutboxEntity segundo = outbox(limite);
        elegiveis(primeiro, segundo);
        doThrow(HttpClientErrorException.create(REQUEST_TIMEOUT, "erro", HttpHeaders.EMPTY, null, null))
                .when(publisher).publicar(timeout);
        doThrow(HttpClientErrorException.create(TOO_MANY_REQUESTS, "erro", HttpHeaders.EMPTY, null, null))
                .when(publisher).publicar(limite);

        dispatcher(0).despacharPendentes();

        assertEquals(agora.plusSeconds(1), primeiro.getProximaTentativaEm());
        assertEquals(agora.plusSeconds(1), segundo.getProximaTentativaEm());
        assertNull(primeiro.getFalhaDefinitivaEm());
        assertNull(segundo.getFalhaDefinitivaEm());
    }

    @Test
    void jsonInvalidoOuNuloEIsoladoSemChamarTransporte() {
        var invalido = new ComandoRegistroOutboxEntity(UUID.randomUUID(), "{invalido", agora);
        var nulo = new ComandoRegistroOutboxEntity(UUID.randomUUID(), "null", agora);
        var semPayload = new ComandoRegistroOutboxEntity(UUID.randomUUID(), null, agora);
        elegiveis(invalido, nulo, semPayload);

        dispatcher(0).despacharPendentes();

        verifyNoInteractions(publisher);
        assertEquals("JSON_INVALIDO", invalido.getUltimoErro());
        assertEquals("COMANDO_NULO", nulo.getUltimoErro());
        assertEquals(agora, invalido.getFalhaDefinitivaEm());
        assertEquals(agora, nulo.getFalhaDefinitivaEm());
        assertEquals("COMANDO_NULO", semPayload.getUltimoErro());
        assertEquals(agora, semPayload.getFalhaDefinitivaEm());
    }

    @Test
    void pendenteLegadoComLimiteJaAtingidoEIsoladoSemNovoEnvio() throws Exception {
        var outbox = outbox(comando());
        for (int i = 0; i < 3; i++) {
            outbox.registrarFalha("HTTP_503", false, agora.minusDays(1));
        }
        elegiveis(outbox);

        dispatcher(0).despacharPendentes();

        verifyNoInteractions(publisher);
        assertEquals(3, outbox.getTentativas());
        assertEquals("LIMITE_TENTATIVAS", outbox.getUltimoErro());
    }

    private void elegiveis(ComandoRegistroOutboxEntity... itens) {
        when(repository.buscarElegiveis(any(), any())).thenReturn(List.of(itens));
    }

    private OutboxDispatcher dispatcher(long segundos) {
        return new OutboxDispatcher(repository, publisher, objectMapper,
                Clock.fixed(inicio.plusSeconds(segundos), ZoneOffset.UTC));
    }

    private RegistrarBoletoCommand comando() {
        UUID id = UUID.randomUUID();
        BoletoAceito aceite = new BoletoAceito(id, StatusBoleto.AGUARDANDO_REGISTRO, agora);
        return new RegistrarBoletoCommand(id, aceite, null);
    }

    private ComandoRegistroOutboxEntity outbox(RegistrarBoletoCommand comando) throws Exception {
        return new ComandoRegistroOutboxEntity(
                comando.identificador(), objectMapper.writeValueAsString(comando), agora);
    }
}
