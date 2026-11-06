package com.vidacruz.triage.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Table("triage_patient")
public record PatientEntity(
        @Id UUID id,
        @Column("nombre_completo") String nombreCompleto,
        Integer edad,
        @Column("frecuencia_cardiaca_bpm") Integer frecuenciaCardiacaBpm,
        @Column("presion_sistolica_mmhg") Integer presionSistolicaMmhg,
        @Column("presion_diastolica_mmhg") Integer presionDiastolicaMmhg,
        BigDecimal spo2,
        @Column("temperatura_c") BigDecimal temperaturaC,
        Integer dolor,
        Integer gcs,
        @Column("queja_principal") String quejaPrincipal,
        @Column("nivel_clasificado") String nivelClasificado,
        @Column("sla_minutos") Integer slaMinutos,
        String estado,
        Boolean escalado,
        @Column("fecha_registro") OffsetDateTime fechaRegistro,
        @Column("fecha_inicio_estado") OffsetDateTime fechaInicioEstado) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }

    // Patient changes use a conditional SQL update; save is reserved for registration.
    @Override
    public boolean isNew() {
        return true;
    }
}
