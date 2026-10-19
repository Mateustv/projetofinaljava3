package br.com.caixa.cobranca.consulta;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class ConsultaExceptionHandler {

    @ExceptionHandler(BoletoNaoEncontradoException.class)
    public ResponseEntity<Void> naoEncontrado() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IdentificadorBoletoInvalidoException.class)
    public ResponseEntity<ErroConsulta> identificadorInvalido(IdentificadorBoletoInvalidoException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroConsulta("IDENTIFICADOR_INVALIDO", ex.getMessage(), null));
    }

    @ExceptionHandler(FalhaFonteBoletoException.class)
    public ResponseEntity<ErroConsulta> falhaTecnica(FalhaFonteBoletoException ex) {
        UUID correlationId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Correlation-ID", correlationId.toString());
        return new ResponseEntity<>(
                new ErroConsulta("FALHA_NA_CONSULTA", "Nao foi possivel consultar o boleto.", correlationId),
                headers,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
