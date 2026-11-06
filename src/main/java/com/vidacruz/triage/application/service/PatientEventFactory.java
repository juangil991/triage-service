package com.vidacruz.triage.application.service;

import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;

import java.time.Instant;
import java.util.UUID;

final class PatientEventFactory {

    private PatientEventFactory() {
    }

    static PatientEvent create(Patient patient, String eventType, String correlationId, Instant occurredAt) {
        return new PatientEvent(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                correlationId,
                patient.id(),
                new PatientEvent.Payload(
                        patient.nivelClasificado(),
                        patient.estado(),
                        patient.fechaRegistro(),
                        patient.slaMinutos(),
                        patient.escalado()));
    }
}
