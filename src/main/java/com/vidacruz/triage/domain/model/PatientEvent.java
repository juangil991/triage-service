package com.vidacruz.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PatientEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        Payload payload) {

    public record Payload(
            TriageLevel triageLevel,
            PatientStatus status,
            Instant registeredAt,
            int slaMinutes,
            boolean escalado) {
    }
}
