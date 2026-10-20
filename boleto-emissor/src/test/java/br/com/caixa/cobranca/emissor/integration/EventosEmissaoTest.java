package br.com.caixa.cobranca.emissor.integration;

import br.com.caixa.cobranca.emissor.exceptions.ChaveIdempotenciaConflitanteException;
import br.com.caixa.cobranca.emissor.exceptions.EmissaoInvalidaException;
import br.com.caixa.cobranca.emissor.exceptions.FalhaAoAceitarEmissaoException;
import br.com.caixa.cobranca.emissor.repositories.ComandoRegistroOutboxRepository;
import br.com.caixa.cobranca.emissor.repositories.EmissaoAceitaRepository;
import br.com.caixa.cobranca.emissor.services.EmissaoService;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import br.com.caixa.cobranca.eventos.EventoOutboxEntity;
import br.com.caixa.cobranca.eventos.EventoOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:eventos-emissao;DB_CLOSE_DELAY=-1")
class EventosEmissaoTest {

    @Autowired
    EmissaoService service;
    @Autowired
    EmissaoAceitaRepository aceites;
    @Autowired
    ComandoRegistroOutboxRepository comandos;
    @Autowired
    ObjectMapper mapper;
    @SpyBean
    EventoOutboxRepository eventos;

    @BeforeEach
    void limpar() {
        eventos.deleteAll();
        comandos.deleteAll();
        aceites.deleteAll();
    }

    @Test
    void aceiteComandoEEventoConfirmamJuntosSemBrokerESemDuplicarEmReenvio() throws Exception {
        var request = request("150.00");
        var aceite = service.aceitar(request);
        var intencao = eventos.findAll().get(0);
        assertEquals(aceite, service.aceitar(request));
        assertEquals(1, aceites.count());
        assertEquals(1, comandos.count());
        assertEquals(1, eventos.count());
        assertEquals(intencao.getIdEvento(), eventos.findAll().get(0).getIdEvento());
        var evento = mapper.readValue(intencao.getEventoJson(), EventoBoleto.class);
        assertEquals(TipoEventoBoleto.EmissaoAceita, evento.tipo());
        assertEquals(aceite.identificador(), evento.identificadorBoleto());
        assertEquals(aceite.identificador(), evento.correlationId());
        assertEquals(request.convenio(), evento.convenio());
        assertEquals(aceite.status(), evento.status());
        assertEquals(aceite.dataHoraRecebimento(), evento.dataHora());
        assertNull(intencao.getPublicadoEm());
        assertEquals(0, intencao.getTentativas());
    }

    @Test
    void falhaDepoisDoFlushDoEventoReverteAceiteComandoEEvento() throws Exception {
        var request = request("150.00");
        doAnswer(invocation -> {
            EventoOutboxEntity entidade = invocation.getArgument(0);
            eventos.save(entidade);
            eventos.flush();
            assertEquals(1, eventos.count());
            throw new DataAccessResourceFailureException("Falha depois do flush do evento.");
        }).when(eventos).saveAndFlush(any(EventoOutboxEntity.class));
        assertThrows(FalhaAoAceitarEmissaoException.class, () -> service.aceitar(request));
        assertEquals(0, aceites.count());
        assertEquals(0, comandos.count());
        assertEquals(0, eventos.count());
        reset(eventos);
        service.aceitar(request);
        assertEquals(1, aceites.count());
        assertEquals(1, comandos.count());
        assertEquals(1, eventos.count());
    }

    @Test
    void conflitoEEntradaInvalidaNaoCriamNovoEvento() throws Exception {
        var original = request("150.00");
        service.aceitar(original);
        var alterado = mapper.readValue(BoletoControllerTest.corpo(
                original.convenio(), original.numeroControleCliente(), "151.00"), EmitirBoletoRequest.class);
        assertThrows(ChaveIdempotenciaConflitanteException.class, () -> service.aceitar(alterado));
        assertThrows(EmissaoInvalidaException.class, () -> service.aceitar(request("0")));
        assertEquals(1, eventos.count());
    }

    private EmitirBoletoRequest request(String valor) throws Exception {
        return mapper.readValue(BoletoControllerTest.corpo("123456",
                "E9-" + UUID.randomUUID().toString().substring(0, 16), valor), EmitirBoletoRequest.class);
    }
}
