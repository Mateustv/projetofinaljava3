package br.com.caixa.cobranca.registro.integration;

import br.com.caixa.cobranca.registro.clients.RegistradoraSimulada;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "registro.simulacao.cenario=REJEITAR",
        "spring.datasource.url=jdbc:h2:mem:registro-rejeitado;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:registro-e4.sql'"
})
@AutoConfigureMockMvc
class RegistroRejeitadoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegistroBoletoRepository repository;

    @SpyBean
    private RegistradoraSimulada registradora;

    @Test
    void rejeicaoEPersistidaComoResultadoDeNegocioEReentregaNaoDuplica() throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        String corpo = objectMapper.writeValueAsString(comando);
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REJEITADO"))
                .andExpect(jsonPath("$.codigo_rejeicao").value("SIM-REJ-001"))
                .andExpect(jsonPath("$.motivo_rejeicao").isNotEmpty());
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"));
        mvc.perform(get("/interno/boletos/{id}", comando.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"))
                .andExpect(jsonPath("$.codigo_rejeicao").value("SIM-REJ-001"))
                .andExpect(jsonPath("$.identificador_registradora").doesNotExist())
                .andExpect(jsonPath("$.data_hora_registro").doesNotExist())
                .andExpect(jsonPath("$.linha_digitavel").doesNotExist())
                .andExpect(jsonPath("$.codigo_barras").doesNotExist());
        assertEquals(1L, repository.count());
        var historico = repository.findById(comando.identificador()).orElseThrow().getHistorico();
        assertEquals(2, historico.size());
        assertEquals(StatusBoleto.AGUARDANDO_REGISTRO, historico.get(0).getStatus());
        assertEquals(StatusBoleto.REJEITADO, historico.get(1).getStatus());
        verify(registradora, times(1)).registrar(comando);
    }
}
