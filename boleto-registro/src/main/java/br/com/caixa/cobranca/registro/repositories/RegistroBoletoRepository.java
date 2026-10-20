package br.com.caixa.cobranca.registro.repositories;

import br.com.caixa.cobranca.registro.models.RegistroBoletoEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroBoletoRepository extends JpaRepository<RegistroBoletoEntity, UUID> {
}
