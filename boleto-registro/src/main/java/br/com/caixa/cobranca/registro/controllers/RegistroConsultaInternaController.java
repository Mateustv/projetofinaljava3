package br.com.caixa.cobranca.registro.controllers;

import br.com.caixa.cobranca.registro.services.RegistroBoletoService;
import br.com.caixa.cobranca.contracts.BoletoConsulta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/interno/boletos")
public class RegistroConsultaInternaController {

    private final RegistroBoletoService service;

    public RegistroConsultaInternaController(RegistroBoletoService service) {
        this.service = service;
    }

    @GetMapping("/{identificador}")
    public ResponseEntity<BoletoConsulta> consultar(@PathVariable UUID identificador) {
        return service.buscar(identificador)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
