package com.vidacruz.triage.infrastructure.adapter.out.persistence.repository;

import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.PatientEntity;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
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
}
