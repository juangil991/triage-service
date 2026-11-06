package com.vidacruz.triage.application.usecase;

import com.vidacruz.triage.application.port.out.ClinicalAuditPort;
import com.vidacruz.triage.application.port.out.OutboxPort;
import com.vidacruz.triage.application.port.out.PatientPersistencePort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.exception.PatientNotFoundException;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import com.vidacruz.triage.domain.service.AdministrativeComplaintMatcher;
import com.vidacruz.triage.domain.service.PatientClassifier;
import com.vidacruz.triage.domain.service.PatientStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientUseCasesTest {

    private final PatientPersistencePort patients = mock(PatientPersistencePort.class);
    private final ClinicalAuditPort audits = mock(ClinicalAuditPort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> Mono<T> withinTransaction(Mono<T> work) {
            return work;
        }
    };
    private RegisterPatientUseCase registerPatient;
    private GetPatientUseCase getPatient;
    private ChangePatientStatusUseCase changePatientStatus;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        registerPatient = new RegisterPatientUseCase(
                patients,
                audits,
                outbox,
                new PatientClassifier(new AdministrativeComplaintMatcher()),
                transactions,
                clock);
        getPatient = new GetPatientUseCase(patients);
        changePatientStatus = new ChangePatientStatusUseCase(
                patients, audits, outbox, new PatientStateMachine(), transactions, clock);
        when(audits.save(any())).thenReturn(Mono.empty());
        when(outbox.save(any())).thenReturn(Mono.empty());
    }

    @Test
    void registrationWritesPatientAuditAndOutboxEvent() {
        when(patients.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(registerPatient.execute(
                        new PatientRegistration(
                                "Paciente de Prueba",
                                40,
                                80,
                                120,
                                80,
                                new BigDecimal("98"),
                                new BigDecimal("37"),
                                2,
                                15,
                                "consulta general"),
                        "correlation-1"))
                .assertNext(patient -> {
                    assertEquals(TriageLevel.NO_URGENTE, patient.nivelClasificado());
                    assertEquals(60, patient.slaMinutos());
                    assertEquals(PatientStatus.REGISTRADO, patient.estado());
                    assertEquals(Instant.parse("2026-01-02T03:04:05Z"), patient.fechaRegistro());
                })
                .verifyComplete();

        verify(audits).save(argThat(audit -> audit.previousStatus() == null
                && audit.newStatus() == PatientStatus.REGISTRADO
                && audit.eventType().equals("paciente_registrado")));
        verify(outbox).save(argThat(event -> event.eventType().equals("paciente_registrado")
                && event.correlationId().equals("correlation-1")));
    }

    @Test
    void patientLookupReturnsNotFoundWhenPatientDoesNotExist() {
        UUID patientId = UUID.randomUUID();
        when(patients.findById(patientId)).thenReturn(Mono.empty());

        StepVerifier.create(getPatient.execute(patientId))
                .expectError(PatientNotFoundException.class)
                .verify();
    }

    @Test
    void validStatusChangeWritesAuditAndOutboxEvent() {
        UUID patientId = UUID.randomUUID();
        Patient original = patient(patientId, PatientStatus.REGISTRADO, Instant.parse("2026-01-02T03:00:00Z"));
        when(patients.findById(patientId)).thenReturn(Mono.just(original));
        when(patients.updateStatus(any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(changePatientStatus.execute(patientId, PatientStatus.EN_CONSULTA, "correlation-2"))
                .assertNext(updated -> {
                    assertEquals(PatientStatus.EN_CONSULTA, updated.estado());
                    assertEquals(Instant.parse("2026-01-02T03:04:05Z"), updated.fechaInicioEstado());
                })
                .verifyComplete();

        verify(audits).save(argThat(audit -> audit.previousStatus() == PatientStatus.REGISTRADO
                && audit.newStatus() == PatientStatus.EN_CONSULTA
                && audit.eventType().equals("paciente_en_consulta")));
        verify(outbox).save(argThat(event -> event.eventType().equals("paciente_en_consulta")));
    }

    @Test
    void invalidTransitionWritesNoAuditOrEvent() {
        UUID patientId = UUID.randomUUID();
        when(patients.findById(patientId)).thenReturn(Mono.just(
                patient(patientId, PatientStatus.ALTA, Instant.parse("2026-01-02T03:00:00Z"))));

        StepVerifier.create(changePatientStatus.execute(patientId, PatientStatus.EN_CONSULTA, "correlation-3"))
                .expectError(InvalidPatientTransitionException.class)
                .verify();

        verify(patients, never()).updateStatus(any(), any());
        verify(audits, never()).save(any(ClinicalAudit.class));
        verify(outbox, never()).save(any(PatientEvent.class));
    }

    private Patient patient(UUID id, PatientStatus status, Instant stateSince) {
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
                60,
                status,
                false,
                Instant.parse("2026-01-02T03:00:00Z"),
                stateSince);
    }
}
