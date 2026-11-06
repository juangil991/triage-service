package com.vidacruz.triage.domain.model;

import java.math.BigDecimal;

public record PatientRegistration(
        String nombreCompleto,
        Integer edad,
        Integer frecuenciaCardiacaBpm,
        Integer presionSistolicaMmhg,
        Integer presionDiastolicaMmhg,
        BigDecimal spo2,
        BigDecimal temperaturaC,
        Integer dolor,
        Integer gcs,
        String quejaPrincipal) {
}
