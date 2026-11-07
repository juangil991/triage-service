package com.vidacruz.triage.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vidacruz.triage.domain.model.EscalationAlert;
import com.vidacruz.triage.domain.model.TriageLevel;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
record EscalationAlertEventMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID patientId,
        Payload payload) {

    EscalationAlert toAlert() {
        if (eventId == null || patientId == null || occurredAt == null
                || correlationId == null || correlationId.isBlank()
                || payload == null || payload.triageLevel() == null || !payload.escalado()) {
            throw new IllegalArgumentException("El evento de escalamiento está incompleto");
        }
        return new EscalationAlert(
                eventId,
                eventType,
                patientId,
                payload.triageLevel(),
                occurredAt,
                payload.slaMinutes(),
                correlationId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(TriageLevel triageLevel, int slaMinutes, boolean escalado) {
    }
}
