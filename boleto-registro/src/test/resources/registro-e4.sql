CREATE TABLE IF NOT EXISTS boleto_registrado (
    identificador UUID PRIMARY KEY,
    comando_json CLOB NOT NULL,
    status VARCHAR(32) NOT NULL,
    data_hora_recebimento TIMESTAMP(6) NOT NULL,
    identificador_registradora VARCHAR(64) NOT NULL,
    data_hora_registro TIMESTAMP(6) NOT NULL
);
