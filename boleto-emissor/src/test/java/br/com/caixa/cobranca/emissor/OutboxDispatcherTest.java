package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private final ComandoRegistroOutboxRepository repository = mock(ComandoRegistroOutboxRepository.class);
    private final RegistroCommandPublisher publisher = mock(RegistroCommandPublisher.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void entregaComandoEMarcaOutboxComoEntregue() throws Exception {
        RegistrarBoletoCommand comando = comando();
        ComandoRegistroOutboxEntity outbox = outbox(comando);
        when(repository.findTop20ByEntregueEmIsNullOrderByCriadoEmAsc()).thenReturn(List.of(outbox));

        new OutboxDispatcher(repository, publisher, objectMapper).despacharPendentes();

        verify(publisher).publicar(comando);
        verify(repository).save(outbox);
        assertNotNull(outbox.getEntregueEm());
        assertNull(outbox.getComandoJson());
        assertEquals(1, outbox.getTentativas());
    }

    @Test
    void falhaDeTransporteMantemComandoPendenteParaNovaTentativa() throws Exception {
        RegistrarBoletoCommand comando = comando();
        ComandoRegistroOutboxEntity outbox = outbox(comando);
        when(repository.findTop20ByEntregueEmIsNullOrderByCriadoEmAsc()).thenReturn(List.of(outbox));
        org.mockito.Mockito.doThrow(new ResourceAccessException("conexao indisponivel"))
                .when(publisher).publicar(comando);

        new OutboxDispatcher(repository, publisher, objectMapper).despacharPendentes();

        verify(repository).save(outbox);
        assertNull(outbox.getEntregueEm());
        assertNotNull(outbox.getComandoJson());
        assertEquals(1, outbox.getTentativas());
        assertEquals("ResourceAccessException", outbox.getUltimoErro());
    }

    private RegistrarBoletoCommand comando() {
        UUID id = UUID.randomUUID();
        BoletoAceito aceite = new BoletoAceito(id, StatusBoleto.AGUARDANDO_REGISTRO, LocalDateTime.now());
        return new RegistrarBoletoCommand(id, aceite, null);
    }

    private ComandoRegistroOutboxEntity outbox(RegistrarBoletoCommand comando) throws Exception {
        return new ComandoRegistroOutboxEntity(
                comando.identificador(),
                objectMapper.writeValueAsString(comando),
                LocalDateTime.now()
        );
    }
}
