package com.vidacruz.triage.infrastructure.adapter.out.persistence.repository;

import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.PatientEntity;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PatientR2dbcRepository extends ReactiveCrudRepository<PatientEntity, UUID> {

    @Query("""
            UPDATE triage_patient
            SET estado = :estado, fecha_inicio_estado = :fechaInicioEstado
            WHERE id = :id AND estado = :estadoAnterior
            RETURNING *
            """)
    Mono<PatientEntity> updateStatus(
            @Param("estado") String estado,
            @Param("fechaInicioEstado") OffsetDateTime fechaInicioEstado,
            @Param("id") UUID id,
            @Param("estadoAnterior") String estadoAnterior);

    @Query("""
            SELECT *
            FROM triage_patient
            WHERE estado = 'REGISTRADO'
              AND escalado = FALSE
              AND fecha_registro + (sla_minutos * INTERVAL '1 minute') <= :now
            ORDER BY fecha_registro
            LIMIT :limit
            """)
    Flux<PatientEntity> findOverdueUnescalated(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit);

    @Query("""
            UPDATE triage_patient
            SET escalado = TRUE
            WHERE id = :id
              AND estado = 'REGISTRADO'
              AND escalado = FALSE
              AND fecha_registro + (sla_minutos * INTERVAL '1 minute') <= :now
            RETURNING *
            """)
    Mono<PatientEntity> markEscalatedIfOverdue(
            @Param("id") UUID id,
            @Param("now") OffsetDateTime now);
}
