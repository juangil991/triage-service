package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.in.EscalateOverduePatientsUseCase;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class EscalateOverduePatientsService implements EscalateOverduePatientsUseCase {

    private static final int BATCH_SIZE = 100;
    private static final String EVENT_TYPE = "paciente_escalado";

    private final PatientRepositoryPort patients;
    private final ClinicalAuditRepositoryPort audits;
    private final OutboxRepositoryPort outbox;
    private final TransactionPort transactions;
    private final Clock clock;

    public EscalateOverduePatientsService(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            TransactionPort transactions,
            Clock clock) {
        this.patients = patients;
        this.audits = audits;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> escalateOverdue() {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            return patients.findOverdueUnescalated(now, BATCH_SIZE)
                    .concatMap(candidate -> transactions.withinTransaction(escalate(candidate, now)))
                    .then();
        });
    }

    private Mono<Void> escalate(Patient candidate, Instant now) {
        return patients.markEscalatedIfOverdue(candidate.id(), now)
                .flatMap(updated -> {
                    ClinicalAudit audit = new ClinicalAudit(
                            UUID.randomUUID(),
                            updated.id(),
                            now,
                            PatientStatus.REGISTRADO,
                            PatientStatus.REGISTRADO,
                            EVENT_TYPE);
                    PatientEvent event = PatientEventFactory.create(
                            updated, EVENT_TYPE, UUID.randomUUID().toString(), now);
                    return audits.save(audit).then(outbox.save(event));
                });
    }
}
