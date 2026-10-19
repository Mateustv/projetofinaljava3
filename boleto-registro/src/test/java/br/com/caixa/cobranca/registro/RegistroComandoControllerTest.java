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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    private RegistrarBoletoCommand comando(UUID identificador, String valor) {
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
