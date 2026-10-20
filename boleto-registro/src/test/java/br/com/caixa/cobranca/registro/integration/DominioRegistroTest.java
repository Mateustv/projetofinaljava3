package br.com.caixa.cobranca.registro.integration;

import br.com.caixa.cobranca.registro.clients.RegistradoraSimulada;
import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroConflitanteException;
import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroInvalidoException;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;
import br.com.caixa.cobranca.registro.repositories.OperacaoRegistradoraSimuladaRepository;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;
import br.com.caixa.cobranca.registro.services.RegistradoraSimuladaPersistence;
import br.com.caixa.cobranca.registro.services.RegistroBoletoService;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registro-dominio;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class DominioRegistroTest {

    @Autowired
    private RegistroBoletoService service;
    @Autowired
    private OperacaoRegistradoraSimuladaRepository operacoes;
    @Autowired
    private RegistradoraSimuladaPersistence simulacao;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @SpyBean
    private RegistroBoletoRepository repository;
    @SpyBean
    private RegistradoraSimulada registradora;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
        operacoes.deleteAll();
    }

    @Test
    void aprovadoPersisteDadosNaoPagaveisEHistoricoSemDuplicar() throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        var primeiro = service.registrar(comando);
        assertEquals(primeiro.boleto(), service.registrar(comando).boleto());

        mvc.perform(get("/interno/boletos/{id}", comando.identificador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linha_digitavel").value("NAO-PAGAVEL-SIM-LINHA-" + comando.identificador()))
                .andExpect(jsonPath("$.codigo_barras").value("NAO-PAGAVEL-SIM-BARRAS-" + comando.identificador()))
                .andExpect(jsonPath("$.codigo_rejeicao").doesNotExist());
        verificarHistorico(comando, StatusBoleto.REGISTRADO);
        assertEquals(1, operacoes.count());
        verify(registradora, times(1)).registrar(comando);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "convenio", "numero_controle_cliente", "beneficiario", "pagador", "valor_nominal",
            "data_emissao", "data_vencimento", "especie",
            "beneficiario.nome", "beneficiario.tipo_documento", "beneficiario.numero_documento",
            "beneficiario.endereco", "pagador.nome", "pagador.tipo_documento", "pagador.numero_documento",
            "pagador.endereco.logradouro", "pagador.endereco.cidade", "pagador.endereco.uf", "pagador.endereco.cep"
    })
    void comandoSemCampoObrigatorioNaoChamaRegistradora(String campo) throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        ObjectNode json = mapper.valueToTree(comando);
        ObjectNode alvo = (ObjectNode) json.get("solicitacao");
        String[] partes = campo.split("\\.");
        for (int i = 0; i < partes.length - 1; i++) {
            alvo = (ObjectNode) alvo.get(partes[i]);
        }
        alvo.putNull(partes[partes.length - 1]);
        mvc.perform(post("/interno/comandos/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(json)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COMANDO_REGISTRO_INVALIDO"));
        verify(registradora, never()).registrar(any());
        assertEquals(0, repository.count());
        assertEquals(0, operacoes.count());
    }

    @Test
    void uuidForaDoContratoV4NaoERegistrado() {
        var comando = RegistroComandoControllerTest.comando(
                UUID.fromString("11111111-1111-1111-8111-111111111111"), "150.00");
        assertThrows(ComandoRegistroInvalidoException.class, () -> service.registrar(comando));
        verify(registradora, never()).registrar(any());
    }

    @ParameterizedTest
    @EnumSource(value = RegistradoraSimulada.Cenario.class, names = {"APROVAR", "REJEITAR"})
    void respostaExternaPerdidaRecuperaMesmoResultadoSemOutroEfeito(RegistradoraSimulada.Cenario cenario) {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        doAnswer(invocation -> {
            simulacao.registrar(comando, cenario);
            throw new FalhaAoRegistrarBoletoException(new IllegalStateException("Resposta perdida."));
        }).when(registradora).registrar(comando);

        assertThrows(FalhaAoRegistrarBoletoException.class, () -> service.registrar(comando));
        assertEquals(0, repository.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        assertEquals(1, operacoes.count());
        var respostaPersistida = simulacao.registrar(comando, RegistradoraSimulada.Cenario.INDISPONIVEL);
        reset(registradora);

        var recuperado = service.registrar(comando);
        assertEquals(respostaPersistida.status(), recuperado.boleto().status());
        assertEquals(respostaPersistida.codigoRejeicao(), recuperado.boleto().codigoRejeicao());
        assertEquals(respostaPersistida.motivoRejeicao(), recuperado.boleto().motivoRejeicao());
        assertEquals(respostaPersistida.dataHoraRegistro(), recuperado.boleto().dataHoraRegistro());
        assertEquals(respostaPersistida.linhaDigitavel(), recuperado.boleto().linhaDigitavel());
        assertEquals(respostaPersistida.codigoBarras(), recuperado.boleto().codigoBarras());
        assertEquals(1, operacoes.count());
        verificarHistorico(comando, respostaPersistida.status());
    }

    @Test
    void rollbackDepoisDoFlushDesfazRegistroEHistoricoMasNaoCommitExterno() {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(tx -> {
            service.registrar(comando);
            assertEquals(2, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
            throw new IllegalStateException("Falha depois de gravar registro e historico.");
        }));
        assertEquals(0, repository.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        assertEquals(1, operacoes.count());
        service.registrar(comando);
        verificarHistorico(comando, StatusBoleto.REGISTRADO);
        assertEquals(1, operacoes.count());
    }

    @Test
    void falhaLocalAposEfeitoExternoNaoPerdeRespostaEReentregaRecupera() {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        doThrow(new DataAccessResourceFailureException("Falha de persistencia simulada."))
                .when(repository).saveAndFlush(any());
        assertThrows(FalhaAoRegistrarBoletoException.class, () -> service.registrar(comando));
        assertEquals(0, repository.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from registro_historico", Integer.class));
        assertEquals(1, operacoes.count());
        reset(repository);
        service.registrar(comando);
        assertEquals(1, operacoes.count());
        verificarHistorico(comando, StatusBoleto.REGISTRADO);
    }

    @Test
    void conteudoConflitanteDepoisDeRespostaPerdidaNaoSobrescreveEfeitoExterno() {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        simulacao.registrar(comando, RegistradoraSimulada.Cenario.APROVAR);
        var alterado = new RegistrarBoletoCommand(comando.identificador(), comando.aceite(),
                RegistroComandoControllerTest.comando(comando.identificador(), "151.00").solicitacao());
        assertThrows(ComandoRegistroConflitanteException.class, () -> service.registrar(alterado));
        assertEquals(0, repository.count());
        assertEquals(1, operacoes.count());
    }

    @Test
    void estadoFinalLegadoNaoEReprocessadoNemRecebeHistoricoInventado() throws Exception {
        var comando = RegistroComandoControllerTest.comando(UUID.randomUUID(), "150.00");
        jdbc.update("""
                insert into boleto_registrado
                (identificador, comando_json, status, data_hora_recebimento,
                 identificador_registradora, data_hora_registro)
                values (?, ?, 'REGISTRADO', ?, ?, ?)
                """, comando.identificador(), mapper.writeValueAsString(comando),
                comando.aceite().dataHoraRecebimento(), "SIM-" + comando.identificador(),
                comando.aceite().dataHoraRecebimento().plusSeconds(5));
        var original = service.buscar(comando.identificador()).orElseThrow();
        var repetido = service.registrar(comando);
        assertEquals(original, repetido.boleto());
        assertEquals(true, repetido.duplicado());
        assertEquals(0, repository.findById(comando.identificador()).orElseThrow().getHistorico().size());
        assertEquals(0, operacoes.count());
        verify(registradora, never()).registrar(any());
    }

    private void verificarHistorico(RegistrarBoletoCommand comando, StatusBoleto finalEsperado) {
        var historico = repository.findById(comando.identificador()).orElseThrow().getHistorico();
        assertEquals(2, historico.size());
        assertEquals(StatusBoleto.AGUARDANDO_REGISTRO, historico.get(0).getStatus());
        assertEquals(comando.aceite().dataHoraRecebimento(), historico.get(0).getOcorridoEm());
        assertEquals(finalEsperado, historico.get(1).getStatus());
        assertFalse(historico.get(1).getOcorridoEm().isBefore(historico.get(0).getOcorridoEm()));
    }
}
