package com.vidacruz.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EscalationAlert(
        UUID eventId,
        String eventType,
        UUID patientId,
        TriageLevel triageLevel,
        Instant escalatedAt,
        int slaMinutes,
        String correlationId) {
}
