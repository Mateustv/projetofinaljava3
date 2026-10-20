package br.com.caixa.cobranca.emissor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ComandoRegistroOutboxRepository extends JpaRepository<ComandoRegistroOutboxEntity, UUID> {

    @Query("""
            select c from ComandoRegistroOutboxEntity c
            where c.entregueEm is null and c.falhaDefinitivaEm is null
              and (c.proximaTentativaEm is null or c.proximaTentativaEm <= :agora)
            order by c.criadoEm, c.identificador
            """)
    List<ComandoRegistroOutboxEntity> buscarElegiveis(@Param("agora") LocalDateTime agora, Pageable limite);

    List<ComandoRegistroOutboxEntity> findTop20ByFalhaDefinitivaEmIsNotNullOrderByFalhaDefinitivaEmDesc();

    long countByIdentificador(UUID identificador);
}
