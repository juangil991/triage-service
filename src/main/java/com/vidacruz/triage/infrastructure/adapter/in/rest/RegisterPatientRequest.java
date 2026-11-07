package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.domain.model.PatientRegistration;

import java.math.BigDecimal;

public record RegisterPatientRequest(
        String nombreCompleto,
        Integer edad,
        Integer frecuenciaCardiaca,
        Integer presionSistolica,
        Integer presionDiastolica,
        BigDecimal spO2,
        BigDecimal temperatura,
        Integer dolor,
        Integer gcs,
        String quejaPrincipal) {

    public PatientRegistration toRegistration() {
        return new PatientRegistration(
                nombreCompleto,
                edad,
                frecuenciaCardiaca,
                presionSistolica,
                presionDiastolica,
                spO2,
                temperatura,
                dolor,
                gcs,
                quejaPrincipal);
    }
}
