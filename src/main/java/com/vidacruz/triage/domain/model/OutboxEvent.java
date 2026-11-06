package com.vidacruz.triage.domain.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String eventType,
        String payload,
        Instant createdAt) {
}
