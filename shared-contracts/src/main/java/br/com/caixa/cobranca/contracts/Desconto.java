package br.com.caixa.cobranca.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Desconto (condicional): exige valor e data limite. */
public record Desconto(
        BigDecimal valor,
        LocalDate ate
) {
}
