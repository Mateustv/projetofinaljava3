package br.com.caixa.cobranca.registro.handlers;

import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroConflitanteException;
import br.com.caixa.cobranca.registro.exceptions.ComandoRegistroInvalidoException;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RegistroExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "codigo", "COMANDO_REGISTRO_INVALIDO",
                "mensagem", "JSON invalido ou com valor fora do formato."
        ));
    }

    @ExceptionHandler(ComandoRegistroInvalidoException.class)
    public ResponseEntity<Map<String, String>> comandoInvalido(ComandoRegistroInvalidoException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "codigo", "COMANDO_REGISTRO_INVALIDO",
                "mensagem", ex.getMessage()
        ));
    }

    @ExceptionHandler(ComandoRegistroConflitanteException.class)
    public ResponseEntity<Map<String, String>> comandoConflitante(ComandoRegistroConflitanteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "codigo", "COMANDO_REGISTRO_CONFLITANTE",
                "mensagem", ex.getMessage()
        ));
    }

    @ExceptionHandler(FalhaAoRegistrarBoletoException.class)
    public ResponseEntity<Map<String, String>> falhaTecnica(FalhaAoRegistrarBoletoException ex) {
        LOGGER.warn("Falha tecnica no registro. tipo={}",
                ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "codigo", "FALHA_NO_REGISTRO",
                "mensagem", ex.getMessage()
        ));
    }
}
