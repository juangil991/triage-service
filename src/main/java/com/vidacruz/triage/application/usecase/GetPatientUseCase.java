package com.vidacruz.triage.application.usecase;

import com.vidacruz.triage.application.port.out.PatientPersistencePort;
import com.vidacruz.triage.domain.exception.PatientNotFoundException;
import com.vidacruz.triage.domain.model.Patient;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetPatientUseCase {

    private final PatientPersistencePort patients;

    public GetPatientUseCase(PatientPersistencePort patients) {
        this.patients = patients;
    }

    public Mono<Patient> execute(UUID patientId) {
        return patients.findById(patientId).switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)));
    }
}
