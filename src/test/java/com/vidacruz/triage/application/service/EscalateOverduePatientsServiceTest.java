package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalateOverduePatientsServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    private final PatientRepositoryPort patients = mock(PatientRepositoryPort.class);
    private final ClinicalAuditRepositoryPort audits = mock(ClinicalAuditRepositoryPort.class);
    private final OutboxRepositoryPort outbox = mock(OutboxRepositoryPort.class);
    private final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> Mono<T> withinTransaction(Mono<T> work) {
            return work;
        }
    };

    private EscalateOverduePatientsService service;

    @BeforeEach
    void setUp() {
        service = new EscalateOverduePatientsService(
                patients,
                audits,
                outbox,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(audits.save(any())).thenReturn(Mono.empty());
        when(outbox.save(any())).thenReturn(Mono.empty());
    }

    @Test
    void successfulConditionalUpdateWritesEscalationAuditAndOutboxEvent() {
        UUID patientId = UUID.randomUUID();
        Patient overdue = patient(patientId);
        when(patients.findOverdueUnescalated(NOW, 100)).thenReturn(Flux.just(overdue));
        when(patients.markEscalatedIfOverdue(patientId, NOW))
                .thenReturn(Mono.just(overdue.withEscalado()));

        StepVerifier.create(service.escalateOverdue()).verifyComplete();

        verify(audits).save(argThat(audit -> isEscalationAudit(audit, patientId)));
        verify(outbox).save(argThat(event -> event.eventType().equals("paciente_escalado")
                && event.patientId().equals(patientId)
                && event.correlationId() != null
                && !event.correlationId().isBlank()
                && event.payload().escalado()));
    }

    @Test
    void conditionalUpdateThatLosesRaceWritesNoAuditOrEvent() {
        UUID patientId = UUID.randomUUID();
        when(patients.findOverdueUnescalated(NOW, 100)).thenReturn(Flux.just(patient(patientId)));
        when(patients.markEscalatedIfOverdue(patientId, NOW)).thenReturn(Mono.empty());

        StepVerifier.create(service.escalateOverdue()).verifyComplete();

        verify(audits, never()).save(any(ClinicalAudit.class));
        verify(outbox, never()).save(any(PatientEvent.class));
    }

    private boolean isEscalationAudit(ClinicalAudit audit, UUID patientId) {
        return audit.patientId().equals(patientId)
                && audit.previousStatus() == PatientStatus.REGISTRADO
                && audit.newStatus() == PatientStatus.REGISTRADO
                && audit.eventType().equals("paciente_escalado")
                && audit.timestamp().equals(NOW);
    }

    private Patient patient(UUID id) {
        return new Patient(
                id,
                "Paciente de Prueba",
                40,
                80,
                120,
                80,
                new BigDecimal("98"),
                new BigDecimal("37"),
                2,
                15,
                "consulta general",
                TriageLevel.NO_URGENTE,
                1,
                PatientStatus.REGISTRADO,
                false,
                Instant.parse("2026-01-02T03:00:00Z"),
                Instant.parse("2026-01-02T03:00:00Z"));
    }
}
