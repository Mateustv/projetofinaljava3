package br.com.caixa.cobranca.registro.integration;

import br.com.caixa.cobranca.broker.BrokerApplication;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.http.demo.Rastros;
import br.com.caixa.cobranca.registro.clients.RegistradoraSimulada;
import br.com.caixa.cobranca.registro.controllers.QuarentenaDemonstracaoController;
import br.com.caixa.cobranca.registro.services.RegistradoraSimuladaPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jms.core.JmsTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemonstracaoRegistroTest {
    @AfterEach
    void limpar() {
        Rastros.desativar();
    }

    @Test
    void prefixosNaoMudamSimulacaoNormalMasEscolhemCenarioNaDemonstracao() {
        var persistencia = mock(RegistradoraSimuladaPersistence.class);
        var client = new RegistradoraSimulada(RegistradoraSimulada.Cenario.APROVAR, persistencia);
        for (String prefixo : new String[]{"DEMO-R-", "DEMO-F-"}) {
            var pedido = new EmitirBoletoRequest("123456", prefixo + "teste", null, null,
                    null, null, null, null, null, null, null, null, null, null);
            var comando = new RegistrarBoletoCommand(UUID.randomUUID(), null, pedido);
            Rastros.desativar();
            client.registrar(comando);
            verify(persistencia).registrar(comando, RegistradoraSimulada.Cenario.APROVAR);
            Rastros.ativar("registro");
            client.registrar(comando);
            verify(persistencia).registrar(comando, prefixo.contains("-R-")
                    ? RegistradoraSimulada.Cenario.REJEITAR : RegistradoraSimulada.Cenario.INDISPONIVEL);
        }
    }

    @Test
    void aprovacaoExplicitaNoPerfilNaoDependeDoCenarioPadrao() {
        var persistencia = mock(RegistradoraSimuladaPersistence.class);
        var client = new RegistradoraSimulada(RegistradoraSimulada.Cenario.INDISPONIVEL, persistencia);
        var pedido = new EmitirBoletoRequest("123456", "DEMO-A-teste", null, null,
                null, null, null, null, null, null, null, null, null, null);
        var comando = new RegistrarBoletoCommand(UUID.randomUUID(), null, pedido);
        Rastros.ativar("registro");
        client.registrar(comando);
        verify(persistencia).registrar(comando, RegistradoraSimulada.Cenario.APROVAR);
    }

    @Test
    void quarentenaEObservadaSemConsumoComLimiteExplicito(@TempDir Path dados) throws Exception {
        var broker = BrokerApplication.criar(dados, "vm://61");
        broker.start();
        try (var factory = new ActiveMQConnectionFactory("vm://61")) {
            var jms = new JmsTemplate(factory);
            jms.setReceiveTimeout(1000);
            UUID boleto = UUID.randomUUID();
            jms.convertAndSend(BrokerApplication.QUARENTENA, "{\"identificador\":\"" + boleto + "\"}");
            var controller = new QuarentenaDemonstracaoController(jms, new ObjectMapper());
            assertTrue(controller.observar(boleto).encontrada());
            assertTrue(controller.observar(boleto).encontrada());
            assertNotNull(jms.receive(BrokerApplication.QUARENTENA));
            assertFalse(controller.observar(boleto).encontrada());
            for (int i = 0; i < 201; i++) {
                jms.convertAndSend(BrokerApplication.QUARENTENA, "{}");
            }
            var observacao = controller.observar(boleto);
            assertEquals(200, observacao.examinadas());
            assertTrue(observacao.limiteAtingido());
        } finally {
            broker.stop();
        }
    }
}
