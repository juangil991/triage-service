package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.domain.model.Patient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String nombreCompleto,
        int edad,
        int frecuenciaCardiacaBpm,
        int presionSistolicaMmhg,
        int presionDiastolicaMmhg,
        BigDecimal spo2,
        BigDecimal temperaturaC,
        int dolor,
        int gcs,
        String quejaPrincipal,
        String nivel,
        int slaMinutos,
        String estado,
        boolean escalado,
        Instant fechaRegistro,
        long tiempoEnEstadoSegundos) {

    public static PatientResponse from(Patient patient, Instant now) {
        return new PatientResponse(
                patient.id(),
                patient.nombreCompleto(),
                patient.edad(),
                patient.frecuenciaCardiacaBpm(),
                patient.presionSistolicaMmhg(),
                patient.presionDiastolicaMmhg(),
                patient.spo2(),
                patient.temperaturaC(),
                patient.dolor(),
                patient.gcs(),
                patient.quejaPrincipal(),
                patient.nivelClasificado().name(),
                patient.slaMinutos(),
                patient.estado().name(),
                patient.escalado(),
                patient.fechaRegistro(),
                Math.max(0, Duration.between(patient.fechaInicioEstado(), now).getSeconds()));
    }
}
