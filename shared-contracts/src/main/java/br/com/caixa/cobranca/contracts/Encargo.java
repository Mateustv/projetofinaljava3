package br.com.caixa.cobranca.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Juros ou multa (condicional): exige tipo, valor e a data a partir da qual vale. */
public record Encargo(
        TipoEncargo tipo,
        BigDecimal valor,
        LocalDate aPartirDe
) {
}
