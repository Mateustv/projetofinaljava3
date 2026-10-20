package br.com.caixa.cobranca.eventos;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventoOutboxRepository extends JpaRepository<EventoOutboxEntity, UUID> {

    @Query("""
            select e from EventoOutboxEntity e
            where e.publicadoEm is null and e.falhaDefinitivaEm is null
              and (e.proximaTentativaEm is null or e.proximaTentativaEm <= :agora)
            order by e.criadoEm, e.idEvento
            """)
    List<EventoOutboxEntity> buscarElegiveis(LocalDateTime agora, Pageable pageable);
}
