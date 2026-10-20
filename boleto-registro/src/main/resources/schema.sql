-- Compatibilidade com os arquivos H2 criados na E4, antes do estado REJEITADO.
ALTER TABLE boleto_registrado ALTER COLUMN identificador_registradora DROP NOT NULL;
ALTER TABLE boleto_registrado ALTER COLUMN data_hora_registro DROP NOT NULL;
