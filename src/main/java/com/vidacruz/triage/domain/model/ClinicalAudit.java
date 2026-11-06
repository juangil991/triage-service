package com.vidacruz.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ClinicalAudit(
        UUID id,
        UUID patientId,
        Instant timestamp,
        PatientStatus previousStatus,
        PatientStatus newStatus,
        String eventType) {
}
