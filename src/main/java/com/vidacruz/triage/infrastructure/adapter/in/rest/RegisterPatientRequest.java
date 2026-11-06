package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.domain.model.PatientRegistration;

import java.math.BigDecimal;

public record RegisterPatientRequest(
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

    public PatientRegistration toRegistration() {
        return new PatientRegistration(
                nombreCompleto,
                edad,
                frecuenciaCardiacaBpm,
                presionSistolicaMmhg,
                presionDiastolicaMmhg,
                spo2,
                temperaturaC,
                dolor,
                gcs,
                quejaPrincipal);
    }
}
