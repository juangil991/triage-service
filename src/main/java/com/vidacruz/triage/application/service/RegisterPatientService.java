package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.in.RegisterPatientUseCase;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import com.vidacruz.triage.domain.service.PatientClassifier;
import com.vidacruz.triage.domain.service.TriageSlaPolicy;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class
RegisterPatientService implements RegisterPatientUseCase {

    private final PatientRepositoryPort patients;
    private final ClinicalAuditRepositoryPort audits;
    private final OutboxRepositoryPort outbox;
    private final PatientClassifier classifier;
    private final TransactionPort transactions;
    private final Clock clock;
    private final TriageSlaPolicy slaPolicy;

    public RegisterPatientService(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            PatientClassifier classifier,
            TransactionPort transactions,
            Clock clock,
            TriageSlaPolicy slaPolicy) {
        this.patients = patients;
        this.audits = audits;
        this.outbox = outbox;
        this.classifier = classifier;
        this.transactions = transactions;
        this.clock = clock;
        this.slaPolicy = slaPolicy;
    }

    @Override
    public Mono<Patient> register(PatientRegistration registration, String correlationId) {
        return Mono.defer(() -> {
            TriageLevel level = classifier.classify(registration);
            Instant now = clock.instant();
            Patient patient = new Patient(
                    UUID.randomUUID(),
                    registration.nombreCompleto().trim(),
                    registration.edad(),
                    registration.frecuenciaCardiacaBpm(),
                    registration.presionSistolicaMmhg(),
                    registration.presionDiastolicaMmhg(),
                    registration.spo2(),
                    registration.temperaturaC(),
                    registration.dolor(),
                    registration.gcs(),
                    registration.quejaPrincipal().trim(),
                    level,
                    slaPolicy.minutesFor(level),
                    PatientStatus.REGISTRADO,
                    false,
                    now,
                    now);

            PatientEvent event = PatientEventFactory.create(patient, "paciente_registrado", correlationId, now);
            return patients.save(patient)
                    .then(audits.save(new ClinicalAudit(
                            UUID.randomUUID(), patient.id(), now, null, PatientStatus.REGISTRADO,
                            "paciente_registrado")))
                    .then(outbox.save(event))
                    .thenReturn(patient);
        }).as(transactions::withinTransaction);
    }
}
