package com.vidacruz.triage.application.usecase;

import com.vidacruz.triage.application.port.out.ClinicalAuditPort;
import com.vidacruz.triage.application.port.out.OutboxPort;
import com.vidacruz.triage.application.port.out.PatientPersistencePort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import com.vidacruz.triage.domain.service.PatientClassifier;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class RegisterPatientUseCase {

    private final PatientPersistencePort patients;
    private final ClinicalAuditPort audits;
    private final OutboxPort outbox;
    private final PatientClassifier classifier;
    private final TransactionPort transactions;
    private final Clock clock;

    public RegisterPatientUseCase(
            PatientPersistencePort patients,
            ClinicalAuditPort audits,
            OutboxPort outbox,
            PatientClassifier classifier,
            TransactionPort transactions,
            Clock clock) {
        this.patients = patients;
        this.audits = audits;
        this.outbox = outbox;
        this.classifier = classifier;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Mono<Patient> execute(PatientRegistration registration, String correlationId) {
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
                level.slaMinutes(),
                PatientStatus.REGISTRADO,
                false,
                now,
                now);

        PatientEvent event = PatientEventFactory.create(patient, "paciente_registrado", correlationId, now);
        return Mono.defer(() -> patients.save(patient)
                .then(audits.save(new ClinicalAudit(
                        UUID.randomUUID(), patient.id(), now, null, PatientStatus.REGISTRADO, "paciente_registrado")))
                .then(outbox.save(event))
                .thenReturn(patient))
                .as(transactions::withinTransaction);
    }
}
