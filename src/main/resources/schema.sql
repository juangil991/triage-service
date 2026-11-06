CREATE TABLE IF NOT EXISTS triage_patient (
    id UUID PRIMARY KEY,
    nombre_completo VARCHAR(100) NOT NULL CHECK (char_length(btrim(nombre_completo)) BETWEEN 3 AND 100),
    edad INTEGER NOT NULL CHECK (edad BETWEEN 0 AND 120),
    frecuencia_cardiaca_bpm INTEGER NOT NULL CHECK (frecuencia_cardiaca_bpm BETWEEN 20 AND 250),
    presion_sistolica_mmhg INTEGER NOT NULL CHECK (presion_sistolica_mmhg BETWEEN 60 AND 260),
    presion_diastolica_mmhg INTEGER NOT NULL CHECK (presion_diastolica_mmhg BETWEEN 30 AND 160),
    spo2 NUMERIC NOT NULL CHECK (spo2 BETWEEN 70 AND 100),
    temperatura_c NUMERIC NOT NULL CHECK (temperatura_c BETWEEN 34.0 AND 42.0),
    dolor INTEGER NOT NULL CHECK (dolor BETWEEN 0 AND 10),
    gcs INTEGER NOT NULL CHECK (gcs BETWEEN 3 AND 15),
    queja_principal TEXT NOT NULL CHECK (length(btrim(queja_principal)) > 0),
    nivel_clasificado VARCHAR(20) NOT NULL
        CHECK (nivel_clasificado IN ('CRITICO', 'URGENTE', 'URGENTE_2', 'NO_URGENTE', 'NO_URGENTE_2')),
    sla_minutos INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('REGISTRADO', 'EN_CONSULTA', 'ALTA')),
    escalado BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro TIMESTAMPTZ NOT NULL,
    fecha_inicio_estado TIMESTAMPTZ NOT NULL,
    CHECK (presion_diastolica_mmhg < presion_sistolica_mmhg)
);

CREATE TABLE IF NOT EXISTS clinical_audit (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES triage_patient(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    send_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

ALTER TABLE outbox_event
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_outbox_event_pending
    ON outbox_event (created_at)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_event_pending_due
    ON outbox_event (next_attempt_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_triage_patient_status
    ON triage_patient (estado, fecha_inicio_estado);
