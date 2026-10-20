package br.com.caixa.cobranca.emissor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/interno/outbox/falhas")
public class OutboxFalhasController {

    private final ComandoRegistroOutboxRepository repository;

    public OutboxFalhasController(ComandoRegistroOutboxRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FalhaOutbox> consultar() {
        return repository.findTop20ByFalhaDefinitivaEmIsNotNullOrderByFalhaDefinitivaEmDesc().stream()
                .map(item -> new FalhaOutbox(item.getIdentificador(), item.getTentativas(),
                        item.getUltimoErro(), item.getFalhaDefinitivaEm()))
                .toList();
    }

    public record FalhaOutbox(UUID identificador, int tentativas, String tipoErro, LocalDateTime falhaEm) {
    }
}
