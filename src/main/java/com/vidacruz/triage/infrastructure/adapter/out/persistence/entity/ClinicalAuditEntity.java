package com.vidacruz.triage.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("clinical_audit")
public record ClinicalAuditEntity(
        @Id UUID id,
        @Column("patient_id") UUID patientId,
        @Column("occurred_at") OffsetDateTime occurredAt,
        @Column("previous_status") String previousStatus,
        @Column("new_status") String newStatus,
        @Column("event_type") String eventType) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }

    // Clinical audit records are append-only.
    @Override
    public boolean isNew() {
        return true;
    }
}
