package br.com.caixa.cobranca.registro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroBoletoRepository extends JpaRepository<RegistroBoletoEntity, UUID> {
}
