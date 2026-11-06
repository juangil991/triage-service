package com.vidacruz.triage.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("outbox_event")
public record OutboxEventEntity(
        @Id UUID id,
        @Column("event_type") String eventType,
        String payload,
        @Column("created_at") OffsetDateTime createdAt,
        @Column("published_at") OffsetDateTime publishedAt,
        @Column("next_attempt_at") OffsetDateTime nextAttemptAt,
        @Column("send_attempts") Integer sendAttempts,
        @Column("last_error") String lastError) {
}
