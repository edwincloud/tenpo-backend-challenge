-- Tabla que guarda el historial de llamadas a la API (requisito 3 del challenge).
CREATE TABLE call_history (
    id               UUID PRIMARY KEY,
    call_timestamp   TIMESTAMPTZ  NOT NULL,
    endpoint         VARCHAR(255) NOT NULL,
    request_params   TEXT,
    response_body    TEXT,
    status_code      INTEGER      NOT NULL,
    error_message    TEXT
);

-- El endpoint de historial siempre ordena por fecha descendente, así que indexamos por eso.
CREATE INDEX idx_call_history_timestamp ON call_history (call_timestamp DESC);
CREATE INDEX idx_call_history_endpoint ON call_history (endpoint);
