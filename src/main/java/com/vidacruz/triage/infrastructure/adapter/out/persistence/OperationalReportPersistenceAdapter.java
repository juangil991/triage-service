package com.vidacruz.triage.infrastructure.adapter.out.persistence;

import com.vidacruz.triage.application.port.out.OperationalReportRepositoryPort;
import com.vidacruz.triage.domain.model.OperationalReportHour;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Repository
public class OperationalReportPersistenceAdapter implements OperationalReportRepositoryPort {

    private static final String REPORT_QUERY = """
            WITH registros AS (
                SELECT
                    date_trunc('hour', paciente.fecha_registro AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS hora,
                    paciente.id,
                    paciente.nivel_clasificado,
                    paciente.fecha_registro,
                    (
                        SELECT MIN(auditoria.occurred_at)
                        FROM clinical_audit auditoria
                        WHERE auditoria.patient_id = paciente.id
                          AND auditoria.event_type = 'paciente_en_consulta'
                    ) AS primera_consulta
                FROM triage_patient paciente
                WHERE paciente.fecha_registro >= :desde
                  AND paciente.fecha_registro < :hasta
            ),
            conteos AS (
                SELECT
                    hora,
                    COUNT(*) AS total_pacientes,
                    COUNT(*) FILTER (WHERE nivel_clasificado = 'CRITICO') AS critico,
                    COUNT(*) FILTER (WHERE nivel_clasificado = 'URGENTE') AS urgente,
                    COUNT(*) FILTER (WHERE nivel_clasificado = 'URGENTE_2') AS urgente_2,
                    COUNT(*) FILTER (WHERE nivel_clasificado = 'NO_URGENTE') AS no_urgente,
                    COUNT(*) FILTER (WHERE nivel_clasificado = 'NO_URGENTE_2') AS no_urgente_2
                FROM registros
                GROUP BY hora
            ),
            esperas AS (
                SELECT
                    hora,
                    EXTRACT(EPOCH FROM (primera_consulta - fecha_registro)) / 60 AS minutos_espera,
                    ROW_NUMBER() OVER (
                        PARTITION BY hora
                        ORDER BY primera_consulta - fecha_registro, id
                    ) AS posicion_p95,
                    COUNT(*) OVER (PARTITION BY hora) AS total_consultados
                FROM registros
                WHERE primera_consulta IS NOT NULL
            ),
            estadisticas_espera AS (
                SELECT
                    hora,
                    AVG(minutos_espera) AS promedio_espera_minutos,
                    MAX(minutos_espera) FILTER (
                        WHERE posicion_p95 = CEIL(total_consultados * 0.95)
                    ) AS p95_espera_minutos
                FROM esperas
                GROUP BY hora
            )
            SELECT
                conteos.hora,
                conteos.total_pacientes,
                conteos.critico,
                conteos.urgente,
                conteos.urgente_2,
                conteos.no_urgente,
                conteos.no_urgente_2,
                estadisticas_espera.promedio_espera_minutos,
                estadisticas_espera.p95_espera_minutos
            FROM conteos
            LEFT JOIN estadisticas_espera USING (hora)
            ORDER BY conteos.hora
            """;

    private final DatabaseClient database;

    public OperationalReportPersistenceAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Flux<OperationalReportHour> findByRegistrationRange(OffsetDateTime desde, OffsetDateTime hasta) {
        return database.sql(REPORT_QUERY)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .map((row, metadata) -> new OperationalReportHour(
                        row.get("hora", OffsetDateTime.class).toInstant(),
                        row.get("total_pacientes", Long.class),
                        row.get("critico", Long.class),
                        row.get("urgente", Long.class),
                        row.get("urgente_2", Long.class),
                        row.get("no_urgente", Long.class),
                        row.get("no_urgente_2", Long.class),
                        row.get("promedio_espera_minutos", BigDecimal.class),
                        row.get("p95_espera_minutos", BigDecimal.class)))
                .all();
    }
}
