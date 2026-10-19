package br.com.caixa.cobranca.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo do POST /boletos (sessao 2). Chave de idempotencia: convenio + numeroControleCliente.
 * JSON em snake_case (configurado no Jackson de cada servico).
 * Campos obrigatorios: convenio .. especie. Condicionais: juros, multa, desconto,
 * abatimento, instrucoes, mensagem.
 */
public record EmitirBoletoRequest(
        String convenio,                 // 6 digitos
        String numeroControleCliente,    // ate 25 caracteres
        Pessoa beneficiario,
        Pessoa pagador,
        BigDecimal valorNominal,         // > 0, ate 2 casas
        LocalDate dataEmissao,
        LocalDate dataVencimento,        // >= dataEmissao
        String especie,                  // DM | DS (premissa)
        Encargo juros,
        Encargo multa,
        Desconto desconto,
        BigDecimal abatimento,
        String instrucoes,
        String mensagem
) {
}
