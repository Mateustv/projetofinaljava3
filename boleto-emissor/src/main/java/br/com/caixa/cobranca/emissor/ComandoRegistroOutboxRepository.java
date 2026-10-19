package br.com.caixa.cobranca.emissor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComandoRegistroOutboxRepository extends JpaRepository<ComandoRegistroOutboxEntity, UUID> {

    List<ComandoRegistroOutboxEntity> findTop20ByEntregueEmIsNullOrderByCriadoEmAsc();

    long countByIdentificador(UUID identificador);
}
