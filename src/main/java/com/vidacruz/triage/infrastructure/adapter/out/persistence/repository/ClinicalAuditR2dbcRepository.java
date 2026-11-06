package com.vidacruz.triage.infrastructure.adapter.out.persistence.repository;

import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.ClinicalAuditEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface ClinicalAuditR2dbcRepository extends ReactiveCrudRepository<ClinicalAuditEntity, UUID> {
}
