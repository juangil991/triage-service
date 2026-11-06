package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.usecase.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.usecase.GetPatientUseCase;
import com.vidacruz.triage.application.usecase.RegisterPatientUseCase;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientInputAdapterTest {

    private final RegisterPatientUseCase registerPatient = mock(RegisterPatientUseCase.class);
    private final GetPatientUseCase getPatient = mock(GetPatientUseCase.class);
    private final ChangePatientStatusUseCase changePatientStatus = mock(ChangePatientStatusUseCase.class);
    private final PatientInputAdapter adapter = new PatientInputAdapter(
            registerPatient, getPatient, changePatientStatus);

    @Test
    void registrationPortDelegatesToApplicationUseCase() {
        Patient patient = patient();
        PatientRegistration registration = new PatientRegistration(
                "Paciente de Prueba",
                40,
                80,
                120,
                80,
                new BigDecimal("98"),
                new BigDecimal("37"),
                2,
                15,
                "consulta general");
        when(registerPatient.execute(registration, "correlation-1")).thenReturn(Mono.just(patient));

        StepVerifier.create(adapter.register(registration, "correlation-1"))
                .expectNext(patient)
                .verifyComplete();

        verify(registerPatient).execute(registration, "correlation-1");
    }

    @Test
    void lookupPortDelegatesToApplicationUseCase() {
        UUID patientId = UUID.randomUUID();
        Patient patient = patient(patientId);
        when(getPatient.execute(patientId)).thenReturn(Mono.just(patient));

        StepVerifier.create(adapter.get(patientId))
                .expectNext(patient)
                .verifyComplete();

        verify(getPatient).execute(patientId);
    }

    @Test
    void statusPortDelegatesToApplicationUseCase() {
        UUID patientId = UUID.randomUUID();
        Patient patient = patient(patientId);
        when(changePatientStatus.execute(patientId, PatientStatus.ALTA, "correlation-2"))
                .thenReturn(Mono.just(patient));

        StepVerifier.create(adapter.changeStatus(patientId, PatientStatus.ALTA, "correlation-2"))
                .expectNext(patient)
                .verifyComplete();

        verify(changePatientStatus).execute(patientId, PatientStatus.ALTA, "correlation-2");
    }

    private Patient patient() {
        return patient(UUID.randomUUID());
    }

    private Patient patient(UUID id) {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
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
                PatientStatus.ALTA,
                false,
                now,
                now);
    }
}
