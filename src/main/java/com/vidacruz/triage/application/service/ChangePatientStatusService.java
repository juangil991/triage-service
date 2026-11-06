package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.in.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.exception.PatientNotFoundException;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.service.PatientStateMachine;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class ChangePatientStatusService implements ChangePatientStatusUseCase {

    private final PatientRepositoryPort patients;
    private final ClinicalAuditRepositoryPort audits;
    private final OutboxRepositoryPort outbox;
    private final PatientStateMachine stateMachine;
    private final TransactionPort transactions;
    private final Clock clock;

    public ChangePatientStatusService(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            PatientStateMachine stateMachine,
            TransactionPort transactions,
            Clock clock) {
        this.patients = patients;
        this.audits = audits;
        this.outbox = outbox;
        this.stateMachine = stateMachine;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Patient> changeStatus(UUID patientId, PatientStatus requestedStatus, String correlationId) {
        return Mono.defer(() -> patients.findById(patientId)
                .switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)))
                .flatMap(current -> {
                    stateMachine.validate(current.estado(), requestedStatus);

                    Instant now = clock.instant();
                    Patient updated = current.withEstado(requestedStatus, now);
                    String eventType = requestedStatus == PatientStatus.EN_CONSULTA
                            ? "paciente_en_consulta"
                            : "paciente_alta";
                    return patients.updateStatus(updated, current.estado())
                            .switchIfEmpty(Mono.error(
                                    new InvalidPatientTransitionException(current.estado(), requestedStatus)))
                            .flatMap(persisted -> {
                                ClinicalAudit audit = new ClinicalAudit(
                                        UUID.randomUUID(), patientId, now, current.estado(), requestedStatus, eventType);
                                PatientEvent event = PatientEventFactory.create(
                                        persisted, eventType, correlationId, now);
                                return audits.save(audit)
                                        .then(outbox.save(event))
                                        .thenReturn(persisted);
                            });
                }))
                .as(transactions::withinTransaction);
    }
}
