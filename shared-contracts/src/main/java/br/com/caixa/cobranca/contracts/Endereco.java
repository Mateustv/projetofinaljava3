package br.com.caixa.cobranca.contracts;

/** Endereco de beneficiario ou pagador. UF com 2 letras maiusculas; CEP com 8 digitos. */
public record Endereco(
        String logradouro,
        String cidade,
        String uf,
        String cep
) {
}
