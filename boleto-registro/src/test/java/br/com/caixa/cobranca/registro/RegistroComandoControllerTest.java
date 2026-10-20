package br.com.caixa.cobranca.registro;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistroComandoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistroBoletoRepository repository;

    @Autowired
    private RegistroBoletoService service;

    @SpyBean
    private RegistradoraSimulada registradora;

    @BeforeEach
    void limparRegistros() {
        repository.deleteAll();
    }

    @Test
    void comandoRegistraBoletoEConsultaInternaDevolveEstadoFinal() throws Exception {
        RegistrarBoletoCommand comando = comando(UUID.randomUUID(), "150.00");

        mvc.perform(post("/interno/comandos/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comando)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REGISTRADO"))
                .andExpect(jsonPath("$.identificador_registradora").value("SIM-" + comando.identificador()));

        mvc.perform(get("/interno/boletos/{identificador}", comando.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value(comando.identificador().toString()))
                .andExpect(jsonPath("$.status").value("REGISTRADO"));
    }

    @Test
    void reentregaDoMesmoComandoEIdempotente() throws Exception {
        RegistrarBoletoCommand comando = comando(UUID.randomUUID(), "150.00");
        String corpo = objectMapper.writeValueAsString(comando);

        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted());
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRADO"));

        org.junit.jupiter.api.Assertions.assertTrue(repository.existsById(comando.identificador()));
        org.junit.jupiter.api.Assertions.assertEquals(1L, repository.count());
    }

    @Test
    void mesmoIdentificadorComOutroComandoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        RegistrarBoletoCommand primeiro = comando(id, "150.00");
        RegistrarBoletoCommand alterado = comando(id, "151.00");

        mvc.perform(post("/interno/comandos/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primeiro)))
                .andExpect(status().isAccepted());
        mvc.perform(post("/interno/comandos/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alterado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COMANDO_REGISTRO_CONFLITANTE"));

        org.junit.jupiter.api.Assertions.assertEquals(1L, repository.count());
    }

    @Test
    void identificadoresDivergentesRetornam400() throws Exception {
        RegistrarBoletoCommand comando = comando(UUID.randomUUID(), "150.00");
        RegistrarBoletoCommand invalido = new RegistrarBoletoCommand(
                UUID.randomUUID(), comando.aceite(), comando.solicitacao());

        mvc.perform(post("/interno/comandos/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COMANDO_REGISTRO_INVALIDO"));
    }

    @Test
    void jsonMalformadoRetorna400SemExporConteudo() throws Exception {
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{DADO_SENSIVEL_SIMULADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COMANDO_REGISTRO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("JSON invalido ou com valor fora do formato."));
        assertEquals(0L, repository.count());
    }

    @Test
    void falhaTecnicaRetorna503SemPersistirEReentregaPodeRecuperar() throws Exception {
        var comando = comando(UUID.randomUUID(), "150.00");
        doThrow(new FalhaAoRegistrarBoletoException(new IllegalStateException("Indisponivel")))
                .doCallRealMethod().when(registradora).registrar(comando);
        String corpo = objectMapper.writeValueAsString(comando);

        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("FALHA_NO_REGISTRO"));
        assertEquals(0L, repository.count());
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REGISTRADO"));
        assertEquals(1L, repository.count());
    }

    @Test
    void reentregasConcorrentesRegistramUmaVez() throws Exception {
        var comando = comando(UUID.randomUUID(), "150.00");
        var inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeiro = executor.submit(() -> {
                inicio.await();
                return service.registrar(comando);
            });
            var segundo = executor.submit(() -> {
                inicio.await();
                return service.registrar(comando);
            });
            inicio.countDown();
            assertEquals(primeiro.get(10, TimeUnit.SECONDS).boleto(),
                    segundo.get(10, TimeUnit.SECONDS).boleto());
            assertEquals(1L, repository.count());
            verify(registradora, times(1)).registrar(comando);
        } finally {
            executor.shutdownNow();
        }
    }

    static RegistrarBoletoCommand comando(UUID identificador, String valor) {
        LocalDateTime recebimento = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        BoletoAceito aceite = new BoletoAceito(
                identificador, StatusBoleto.AGUARDANDO_REGISTRO, recebimento);
        EmitirBoletoRequest solicitacao = new EmitirBoletoRequest(
                "123456",
                "NF-REG-" + identificador.toString().substring(0, 8),
                null,
                null,
                new BigDecimal(valor),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                "DM",
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new RegistrarBoletoCommand(identificador, aceite, solicitacao);
    }
}
