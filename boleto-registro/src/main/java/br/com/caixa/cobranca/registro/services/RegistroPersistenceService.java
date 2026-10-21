package br.com.caixa.cobranca.registro.services;

import br.com.caixa.cobranca.registro.clients.RegistradoraClient;
import br.com.caixa.cobranca.registro.dtos.ResultadoRegistradora;
import br.com.caixa.cobranca.registro.exceptions.FalhaAoRegistrarBoletoException;
import br.com.caixa.cobranca.registro.models.RegistroBoletoEntity;
import br.com.caixa.cobranca.registro.repositories.RegistroBoletoRepository;
import br.com.caixa.cobranca.contracts.RegistrarBoletoCommand;
import br.com.caixa.cobranca.contracts.EventoBoleto;
import br.com.caixa.cobranca.contracts.TipoEventoBoleto;
import br.com.caixa.cobranca.contracts.StatusBoleto;
import br.com.caixa.cobranca.eventos.EventoOutboxService;
import br.com.caixa.cobranca.http.demo.Rastros;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RegistroPersistenceService {

    private final RegistroBoletoRepository repository;
    private final RegistradoraClient registradora;
    private final ObjectMapper objectMapper;
    private final EventoOutboxService eventos;

    public RegistroPersistenceService(RegistroBoletoRepository repository,
                                     RegistradoraClient registradora,
                                     ObjectMapper objectMapper, EventoOutboxService eventos) {
        this.repository = repository;
        this.registradora = registradora;
        this.objectMapper = objectMapper;
        this.eventos = eventos;
    }

    @Transactional
    public RegistroBoletoEntity registrar(RegistrarBoletoCommand comando) {
        ResultadoRegistradora resultado = registradora.registrar(comando);
        try {
            String comandoJson = objectMapper.writeValueAsString(comando);
            var entidade = repository.saveAndFlush(new RegistroBoletoEntity(
                    comando.identificador(),
                    comandoJson,
                    comando.aceite().dataHoraRecebimento().truncatedTo(ChronoUnit.MICROS),
                    resultado
            ));
            eventos.adicionar(new EventoBoleto(UUID.randomUUID(),
                    resultado.status() == StatusBoleto.REGISTRADO
                            ? TipoEventoBoleto.BoletoRegistrado : TipoEventoBoleto.RegistroRejeitado,
                    comando.identificador(), comando.solicitacao().convenio(), resultado.status(),
                    LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), comando.correlationId()));
            Rastros.aposCommit(comando.identificador(), comando.correlationId(), "RESULTADO_PERSISTIDO",
                    "Commit H2: " + resultado.status() + " + historico + evento outbox.");
            return entidade;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new FalhaAoRegistrarBoletoException(ex);
        }
    }
}
