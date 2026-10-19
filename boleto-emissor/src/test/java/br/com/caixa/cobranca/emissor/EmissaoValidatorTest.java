package br.com.caixa.cobranca.emissor;

import br.com.caixa.cobranca.contracts.CampoInvalido;
import br.com.caixa.cobranca.contracts.Desconto;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import br.com.caixa.cobranca.contracts.Encargo;
import br.com.caixa.cobranca.contracts.Endereco;
import br.com.caixa.cobranca.contracts.Pessoa;
import br.com.caixa.cobranca.contracts.TipoDocumento;
import br.com.caixa.cobranca.contracts.TipoEncargo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste puro (sem Spring): regras de validacao da sessao 2. */
class EmissaoValidatorTest {

    private final EmissaoValidator validator = new EmissaoValidator();

    private static final Endereco ENDERECO = new Endereco("Rua A, 1", "Brasilia", "DF", "70000000");
    private static final Pessoa BENEFICIARIO = new Pessoa("Empresa X", TipoDocumento.CNPJ, "12345678000195", ENDERECO);
    private static final Pessoa PAGADOR = new Pessoa("Maria", TipoDocumento.CPF, "12345678909", ENDERECO);
    private static final LocalDate EMISSAO = LocalDate.of(2026, 10, 19);

    /** Requisicao valida minima; os testes derivam dela. */
    private EmitirBoletoRequest valida() {
        return new EmitirBoletoRequest("123456", "NF-0001", BENEFICIARIO, PAGADOR,
                new BigDecimal("150.00"), EMISSAO, EMISSAO.plusDays(30), "DM",
                null, null, null, null, null, null);
    }

    private EmitirBoletoRequest com(String convenio, Pessoa pagador, BigDecimal valor,
                                    LocalDate vencimento, String especie,
                                    Encargo juros, Desconto desconto) {
        return new EmitirBoletoRequest(convenio, "NF-0001", BENEFICIARIO, pagador,
                valor, EMISSAO, vencimento, especie, juros, null, desconto, null, null, null);
    }

    private boolean temErroEm(List<CampoInvalido> erros, String campo) {
        return erros.stream().anyMatch(e -> e.campo().equals(campo));
    }

    @Test
    void requisicaoValidaNaoTemErros() {
        assertTrue(validator.validar(valida()).isEmpty());
    }

    @Test
    void convenioDeveTerSeisDigitos() {
        var erros = validator.validar(com("12345", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "DM", null, null));
        assertTrue(temErroEm(erros, "convenio"));
    }

    @Test
    void valorDeveSerMaiorQueZeroComDuasCasas() {
        assertTrue(temErroEm(validator.validar(com("123456", PAGADOR, BigDecimal.ZERO,
                EMISSAO.plusDays(1), "DM", null, null)), "valor_nominal"));
        assertTrue(temErroEm(validator.validar(com("123456", PAGADOR, new BigDecimal("10.001"),
                EMISSAO.plusDays(1), "DM", null, null)), "valor_nominal"));
    }

    @Test
    void vencimentoNaoPodeSerAnteriorAEmissao() {
        var erros = validator.validar(com("123456", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.minusDays(1), "DM", null, null));
        assertTrue(temErroEm(erros, "data_vencimento"));
    }

    @Test
    void especieDesconhecidaERejeitada() {
        var erros = validator.validar(com("123456", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "XX", null, null));
        assertTrue(temErroEm(erros, "especie"));
    }

    @Test
    void documentoDoPagadorDeveCombinarComOTipo() {
        Pessoa cpfCom14 = new Pessoa("Maria", TipoDocumento.CPF, "12345678000195", ENDERECO);
        var erros = validator.validar(com("123456", cpfCom14, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "DM", null, null));
        assertTrue(temErroEm(erros, "pagador.numero_documento"));
    }

    @Test
    void jurosSemComplementosGeraErroPorCampo() {
        Encargo jurosIncompleto = new Encargo(null, null, null);
        var erros = validator.validar(com("123456", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "DM", jurosIncompleto, null));
        assertTrue(temErroEm(erros, "juros.tipo"));
        assertTrue(temErroEm(erros, "juros.valor"));
        assertTrue(temErroEm(erros, "juros.a_partir_de"));
    }

    @Test
    void jurosCompletoNaoGeraErro() {
        Encargo juros = new Encargo(TipoEncargo.PERCENTUAL, new BigDecimal("1.00"), EMISSAO.plusDays(31));
        assertTrue(validator.validar(com("123456", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "DM", juros, null)).isEmpty());
    }

    @Test
    void descontoSemDataLimiteGeraErro() {
        var erros = validator.validar(com("123456", PAGADOR, new BigDecimal("10.00"),
                EMISSAO.plusDays(1), "DM", null, new Desconto(new BigDecimal("1.00"), null)));
        assertTrue(temErroEm(erros, "desconto.ate"));
    }

    @Test
    void acumulaTodosOsErrosEmVezDeParar() {
        // convenio invalido + valor invalido + especie invalida => 3 erros de uma vez
        var erros = validator.validar(com("1", PAGADOR, BigDecimal.ZERO,
                EMISSAO.plusDays(1), "XX", null, null));
        assertEquals(3, erros.size());
    }

    @Test
    void corpoNuloGeraErro() {
        assertTrue(temErroEm(validator.validar(null), "corpo"));
    }
}
