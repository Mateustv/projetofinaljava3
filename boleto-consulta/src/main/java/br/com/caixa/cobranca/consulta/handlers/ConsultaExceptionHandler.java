package br.com.caixa.cobranca.consulta.handlers;

import br.com.caixa.cobranca.consulta.dtos.ErroConsulta;
import br.com.caixa.cobranca.consulta.exceptions.BoletoNaoEncontradoException;
import br.com.caixa.cobranca.consulta.exceptions.FalhaFonteBoletoException;
import br.com.caixa.cobranca.consulta.exceptions.IdentificadorBoletoInvalidoException;
import br.com.caixa.cobranca.http.Correlacao;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ConsultaExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsultaExceptionHandler.class);
    @ExceptionHandler(BoletoNaoEncontradoException.class)
    public ResponseEntity<Void> naoEncontrado() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IdentificadorBoletoInvalidoException.class)
    public ResponseEntity<ErroConsulta> identificadorInvalido(IdentificadorBoletoInvalidoException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroConsulta("IDENTIFICADOR_INVALIDO", ex.getMessage(), Correlacao.atualOu(UUID.randomUUID())));
    }

    @ExceptionHandler(FalhaFonteBoletoException.class)
    public ResponseEntity<ErroConsulta> falhaTecnica(FalhaFonteBoletoException ex) {
        UUID correlationId = Correlacao.atualOu(UUID.randomUUID());
        LOGGER.warn("Consulta indisponivel. correlation_id={}, tipo={}", correlationId,
                ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Correlation-ID", correlationId.toString());
        return new ResponseEntity<>(
                new ErroConsulta("FALHA_NA_CONSULTA", "Nao foi possivel consultar o boleto.", correlationId),
                headers,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
