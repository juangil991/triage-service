package com.vidacruz.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Patient(
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
        TriageLevel nivelClasificado,
        int slaMinutos,
        PatientStatus estado,
        boolean escalado,
        Instant fechaRegistro,
        Instant fechaInicioEstado) {

    public Patient withEstado(PatientStatus nuevoEstado, Instant fechaCambio) {
        return new Patient(
                id,
                nombreCompleto,
                edad,
                frecuenciaCardiacaBpm,
                presionSistolicaMmhg,
                presionDiastolicaMmhg,
                spo2,
                temperaturaC,
                dolor,
                gcs,
                quejaPrincipal,
                nivelClasificado,
                slaMinutos,
                nuevoEstado,
                escalado,
                fechaRegistro,
                fechaCambio);
    }
}
