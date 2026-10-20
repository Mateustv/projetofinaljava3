package br.com.caixa.cobranca.registro.integration;

import br.com.caixa.cobranca.registro.clients.RegistradoraSimulada;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;
import br.com.caixa.cobranca.registro.repositories.OperacaoRegistradoraSimuladaRepository;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;
import br.com.caixa.cobranca.registro.services.RegistradoraSimuladaPersistence;
import br.com.caixa.cobranca.registro.services.RegistroBoletoService;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.eventos.EventoOutboxEntity;
import br.com.caixa.cobranca.eventos.EventoOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos-registro;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EventosRegistroTest {

    @Autowired
    RegistroBoletoService service;
    @Autowired
    RegistroBoletoRepository registros;
    @Autowired
    OperacaoRegistradoraSimuladaRepository operacoes;
    @Autowired
    RegistradoraSimuladaPersistence simulacao;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    JdbcTemplate jdbc;
    @SpyBean
    EventoOutboxRepository eventos;
    @SpyBean
    RegistradoraSimulada registradora;

    @BeforeEach
    void limpar() {
        eventos.deleteAll();
        registros.deleteAll();
        operacoes.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(value = RegistradoraSimulada.Cenario.class, names = {"APROVAR", "REJEITAR"})
    void resultadoHistoricoEEventoUnicosSemDuplicarNaReentrega(RegistradoraSimulada.Cenario cenario) throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        doAnswer(invocation -> simulacao.registrar(comando, cenario)).when(registradora).registrar(comando);
        var primeiro = service.registrar(comando);
        var intencao = eventos.findAll().get(0);
        var evento = mapper.readValue(intencao.getEventoJson(), EventoBoleto.class);
        assertEquals(cenario == RegistradoraSimulada.Cenario.APROVAR
                ? TipoEventoBoleto.BoletoRegistrado : TipoEventoBoleto.RegistroRejeitado, evento.tipo());
        assertEquals(primeiro.boleto().status(), evento.status());
        assertEquals(comando.identificador(), evento.identificadorBoleto());
        assertEquals(comando.identificador(), evento.correlationId());
        assertEquals(comando.solicitacao().convenio(), evento.convenio());
        assertEquals(primeiro.boleto(), service.registrar(comando).boleto());
        assertEquals(1, eventos.count());
        assertEquals(intencao.getIdEvento(), eventos.findAll().get(0).getIdEvento());
        assertEquals(1, operacoes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        assertNull(intencao.getPublicadoEm());
        verify(registradora, times(1)).registrar(comando);
    }

    @Test
    void mudancaDeCorrelacaoNaoMudaIdentidadeNemRepeteEfeitoOuEvento() throws Exception {
        var base = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        UUID correlation = UUID.randomUUID();
        var comando = new RegistrarBoletoCommand(base.identificador(), base.aceite(), base.solicitacao(), correlation);
        var original = service.registrar(comando);
        var outraCorrelacao = new RegistrarBoletoCommand(
                base.identificador(), base.aceite(), base.solicitacao(), UUID.randomUUID());
        assertEquals(original.boleto(), service.registrar(outraCorrelacao).boleto());
        assertEquals(1, eventos.count());
        assertEquals(correlation, mapper.readValue(eventos.findAll().get(0).getEventoJson(), EventoBoleto.class).correlationId());
        assertEquals(1, operacoes.count());
        assertEquals(original.boleto().status(),
                simulacao.registrar(outraCorrelacao, RegistradoraSimulada.Cenario.REJEITAR).status());
        verify(registradora, times(1)).registrar(comando);
    }

    @Test
    void comandoLegadoSemCampoCorrelationContinuaIdempotente() throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        var json = mapper.valueToTree(comando);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("correlation_id");
        var legado = mapper.treeToValue(json, RegistrarBoletoCommand.class);
        assertEquals(comando.identificador(), legado.correlationId());
        service.registrar(legado);
        jdbc.update("update boleto_registrado set comando_json = ? where identificador = ?",
                mapper.writeValueAsString(json), comando.identificador());
        jdbc.update("update registradora_simulada_operacao set comando_json = ? where identificador = ?",
                mapper.writeValueAsString(json), comando.identificador());
        var novo = new RegistrarBoletoCommand(comando.identificador(), comando.aceite(),
                comando.solicitacao(), UUID.randomUUID());
        assertTrue(service.registrar(novo).duplicado());
        assertEquals(1, eventos.count());
        assertNotNull(simulacao.registrar(novo, RegistradoraSimulada.Cenario.APROVAR));
        assertEquals(1, operacoes.count());
    }

    @Test
    void falhaDaIntencaoDepoisDeFlushReverteResultadoHistoricoEEventoMasNaoLedger() {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        doAnswer(invocation -> {
            EventoOutboxEntity entidade = invocation.getArgument(0);
            eventos.save(entidade);
            eventos.flush();
            throw new DataAccessResourceFailureException("Falha na intencao do evento.");
        }).when(eventos).saveAndFlush(any(EventoOutboxEntity.class));
        assertThrows(FalhaAoRegistrarBoletoException.class, () -> service.registrar(comando));
        assertEquals(0, registros.count());
        assertEquals(0, eventos.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        assertEquals(1, operacoes.count());
        reset(eventos);
        service.registrar(comando);
        assertEquals(1, registros.count());
        assertEquals(1, eventos.count());
        assertEquals(1, operacoes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
    }

    @Test
    void rollbackExternoDepoisDeGravarIntencaoNaoDeixaEventoFantasma() {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(tx -> {
            service.registrar(comando);
            assertEquals(1, eventos.count());
            throw new IllegalStateException("Rollback local.");
        }));
        assertEquals(0, registros.count());
        assertEquals(0, eventos.count());
        assertEquals(1, operacoes.count());
        service.registrar(comando);
        assertEquals(1, eventos.count());
        assertEquals(1, operacoes.count());
    }

    @Test
    void finalLegadoNaoGeraEventoRetroativo() throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        jdbc.update("""
                insert into boleto_registrado
                (identificador, comando_json, status, data_hora_recebimento,
                 identificador_registradora, data_hora_registro)
                values (?, ?, 'REGISTRADO', ?, ?, ?)
                """, comando.identificador(), mapper.writeValueAsString(comando),
                comando.aceite().dataHoraRecebimento(), "SIM-" + comando.identificador(),
                comando.aceite().dataHoraRecebimento().plusSeconds(5));
        assertTrue(service.registrar(comando).duplicado());
        assertEquals(0, eventos.count());
        assertEquals(0, operacoes.count());
        verify(registradora, never()).registrar(any());
    }
}
