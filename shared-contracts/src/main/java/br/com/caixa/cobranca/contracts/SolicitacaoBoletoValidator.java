package br.com.caixa.cobranca.contracts;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validacao SINCRONA da emissao (sessao 2): coleta TODOS os campos invalidos, nao so o primeiro.
 * Nomes de campo em snake_case (como no JSON). Nunca devolve o valor recebido (pode ser dado pessoal).
 * Rejeicoes da registradora NAO passam por aqui: sao assincronas (estado REJEITADO).
 */
public class SolicitacaoBoletoValidator {

    private static final Pattern CONVENIO = Pattern.compile("\\d{6}");
    private static final Pattern CEP = Pattern.compile("\\d{8}");
    private static final Pattern UF = Pattern.compile("[A-Z]{2}");
    private static final int MAX_NUMERO_CONTROLE = 25;
    // PREMISSA: lista de especies a validar com o contrato da registradora.
    private static final Set<String> ESPECIES = Set.of("DM", "DS");

    public List<CampoInvalido> validar(EmitirBoletoRequest r) {
        List<CampoInvalido> erros = new ArrayList<>();

        if (r == null) {
            erros.add(new CampoInvalido("corpo", "obrigatorio"));
            return erros;
        }

        if (vazio(r.convenio()) || !CONVENIO.matcher(r.convenio()).matches()) {
            erros.add(new CampoInvalido("convenio", "obrigatorio, 6 digitos"));
        }
        if (vazio(r.numeroControleCliente()) || r.numeroControleCliente().length() > MAX_NUMERO_CONTROLE) {
            erros.add(new CampoInvalido("numero_controle_cliente", "obrigatorio, ate 25 caracteres"));
        }

        validarPessoa("beneficiario", r.beneficiario(), erros);
        validarPessoa("pagador", r.pagador(), erros);

        if (r.valorNominal() == null || r.valorNominal().signum() <= 0 || r.valorNominal().scale() > 2) {
            erros.add(new CampoInvalido("valor_nominal", "obrigatorio, maior que zero, ate 2 casas decimais"));
        }

        if (r.dataEmissao() == null) {
            erros.add(new CampoInvalido("data_emissao", "obrigatorio"));
        }
        if (r.dataVencimento() == null) {
            erros.add(new CampoInvalido("data_vencimento", "obrigatorio"));
        }
        if (r.dataEmissao() != null && r.dataVencimento() != null
                && r.dataVencimento().isBefore(r.dataEmissao())) {
            erros.add(new CampoInvalido("data_vencimento", "nao pode ser anterior a data_emissao"));
        }

        if (vazio(r.especie()) || !ESPECIES.contains(r.especie())) {
            erros.add(new CampoInvalido("especie", "obrigatoria, valores aceitos: DM, DS"));
        }

        // Condicionais: so validados se informados.
        validarEncargo("juros", r.juros(), erros);
        validarEncargo("multa", r.multa(), erros);
        validarDesconto(r.desconto(), erros);
        if (r.abatimento() != null && r.abatimento().signum() <= 0) {
            erros.add(new CampoInvalido("abatimento", "deve ser maior que zero"));
        }

        return erros;
    }

    private void validarPessoa(String prefixo, Pessoa p, List<CampoInvalido> erros) {
        if (p == null) {
            erros.add(new CampoInvalido(prefixo, "obrigatorio"));
            return;
        }
        if (vazio(p.nome())) {
            erros.add(new CampoInvalido(prefixo + ".nome", "obrigatorio"));
        }
        if (p.tipoDocumento() == null) {
            erros.add(new CampoInvalido(prefixo + ".tipo_documento", "obrigatorio, CPF ou CNPJ"));
        }
        if (vazio(p.numeroDocumento()) || !p.numeroDocumento().matches("\\d+")) {
            erros.add(new CampoInvalido(prefixo + ".numero_documento", "obrigatorio, somente digitos"));
        } else if (p.tipoDocumento() != null && !tamanhoDocumentoOk(p.tipoDocumento(), p.numeroDocumento())) {
            erros.add(new CampoInvalido(prefixo + ".numero_documento",
                    "tamanho incompativel com o tipo_documento (CPF 11, CNPJ 14)"));
        }
        validarEndereco(prefixo + ".endereco", p.endereco(), erros);
    }

    private boolean tamanhoDocumentoOk(TipoDocumento tipo, String numero) {
        return tipo == TipoDocumento.CPF ? numero.length() == 11 : numero.length() == 14;
    }

    private void validarEndereco(String prefixo, Endereco e, List<CampoInvalido> erros) {
        if (e == null) {
            erros.add(new CampoInvalido(prefixo, "obrigatorio"));
            return;
        }
        if (vazio(e.logradouro())) {
            erros.add(new CampoInvalido(prefixo + ".logradouro", "obrigatorio"));
        }
        if (vazio(e.cidade())) {
            erros.add(new CampoInvalido(prefixo + ".cidade", "obrigatorio"));
        }
        if (vazio(e.uf()) || !UF.matcher(e.uf()).matches()) {
            erros.add(new CampoInvalido(prefixo + ".uf", "obrigatoria, 2 letras maiusculas"));
        }
        if (vazio(e.cep()) || !CEP.matcher(e.cep()).matches()) {
            erros.add(new CampoInvalido(prefixo + ".cep", "obrigatorio, 8 digitos"));
        }
    }

    private void validarEncargo(String nome, Encargo e, List<CampoInvalido> erros) {
        if (e == null) {
            return;
        }
        if (e.tipo() == null) {
            erros.add(new CampoInvalido(nome + ".tipo", "obrigatorio quando " + nome + " informado, FIXO ou PERCENTUAL"));
        }
        if (naoPositivo(e.valor())) {
            erros.add(new CampoInvalido(nome + ".valor", "obrigatorio quando " + nome + " informado, maior que zero"));
        }
        if (e.aPartirDe() == null) {
            erros.add(new CampoInvalido(nome + ".a_partir_de", "obrigatoria quando " + nome + " informado"));
        }
    }

    private void validarDesconto(Desconto d, List<CampoInvalido> erros) {
        if (d == null) {
            return;
        }
        if (naoPositivo(d.valor())) {
            erros.add(new CampoInvalido("desconto.valor", "obrigatorio quando desconto informado, maior que zero"));
        }
        if (d.ate() == null) {
            erros.add(new CampoInvalido("desconto.ate", "obrigatoria quando desconto informado"));
        }
    }

    /** true quando o valor e INVALIDO (nulo ou nao positivo). */
    private boolean naoPositivo(BigDecimal v) {
        return v == null || v.signum() <= 0;
    }

    private boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
