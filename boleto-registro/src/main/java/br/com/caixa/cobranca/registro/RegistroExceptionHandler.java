package br.com.caixa.cobranca.registro;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RegistroExceptionHandler {

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
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "codigo", "FALHA_NO_REGISTRO",
                "mensagem", ex.getMessage()
        ));
    }
}
