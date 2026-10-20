package br.com.caixa.cobranca.emissor.controllers;

import br.com.caixa.cobranca.emissor.services.EmissaoService;
import br.com.caixa.cobranca.contracts.BoletoAceito;
import br.com.caixa.cobranca.contracts.EmitirBoletoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de emissao (sistema a sistema). 202 = aceito para processamento, nao registrado. */
@RestController
@RequestMapping("/boletos")
public class BoletoController {

    private final EmissaoService emissao;

    public BoletoController(EmissaoService emissao) {
        this.emissao = emissao;
    }

    @PostMapping
    public ResponseEntity<BoletoAceito> emitir(@RequestBody EmitirBoletoRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(emissao.aceitar(request));
    }
}
