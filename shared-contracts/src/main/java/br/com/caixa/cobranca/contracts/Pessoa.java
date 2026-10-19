package br.com.caixa.cobranca.contracts;

/** Beneficiario (quem cobra) ou pagador (quem deve pagar). */
public record Pessoa(
        String nome,
        TipoDocumento tipoDocumento,
        String numeroDocumento,   // somente digitos: CPF 11, CNPJ 14
        Endereco endereco
) {
}
