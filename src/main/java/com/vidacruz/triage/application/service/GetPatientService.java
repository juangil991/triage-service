package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.in.GetPatientUseCase;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.domain.exception.PatientNotFoundException;
import com.vidacruz.triage.domain.model.Patient;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetPatientService implements GetPatientUseCase {

    private final PatientRepositoryPort patients;

    public GetPatientService(PatientRepositoryPort patients) {
        this.patients = patients;
    }

    @Override
    public Mono<Patient> get(UUID patientId) {
        return patients.findById(patientId).switchIfEmpty(Mono.error(new PatientNotFoundException(patientId)));
    }
}
