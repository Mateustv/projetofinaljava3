package br.com.caixa.cobranca.emissor.repositories;

import br.com.caixa.cobranca.emissor.models.EmissaoAceitaEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmissaoAceitaRepository extends JpaRepository<EmissaoAceitaEntity, UUID> {

    Optional<EmissaoAceitaEntity> findByConvenioAndNumeroControleCliente(String convenio,
                                                                         String numeroControleCliente);

    long countByConvenioAndNumeroControleCliente(String convenio, String numeroControleCliente);
}
