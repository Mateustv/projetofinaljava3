package br.com.caixa.cobranca.registro.repositories;

import br.com.caixa.cobranca.registro.models.OperacaoRegistradoraSimuladaEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperacaoRegistradoraSimuladaRepository
        extends JpaRepository<OperacaoRegistradoraSimuladaEntity, UUID> {
}
