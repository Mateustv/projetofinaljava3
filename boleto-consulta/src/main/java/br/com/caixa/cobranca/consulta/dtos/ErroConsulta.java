package br.com.caixa.cobranca.consulta.dtos;

import java.util.UUID;

public record ErroConsulta(String codigo, String mensagem, UUID correlationId) {
}
