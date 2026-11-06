package com.vidacruz.triage.application.port.out;

import com.vidacruz.triage.domain.model.ClinicalAudit;
import reactor.core.publisher.Mono;

public interface ClinicalAuditRepositoryPort {

    Mono<Void> save(ClinicalAudit audit);
}
