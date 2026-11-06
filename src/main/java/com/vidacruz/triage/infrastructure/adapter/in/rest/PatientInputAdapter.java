package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.port.in.ChangePatientStatusInputPort;
import com.vidacruz.triage.application.port.in.GetPatientInputPort;
import com.vidacruz.triage.application.port.in.RegisterPatientInputPort;
import com.vidacruz.triage.application.usecase.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.usecase.GetPatientUseCase;
import com.vidacruz.triage.application.usecase.RegisterPatientUseCase;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class PatientInputAdapter implements
        RegisterPatientInputPort,
        GetPatientInputPort,
        ChangePatientStatusInputPort {

    private final RegisterPatientUseCase registerPatient;
    private final GetPatientUseCase getPatient;
    private final ChangePatientStatusUseCase changePatientStatus;

    public PatientInputAdapter(
            RegisterPatientUseCase registerPatient,
            GetPatientUseCase getPatient,
            ChangePatientStatusUseCase changePatientStatus) {
        this.registerPatient = registerPatient;
        this.getPatient = getPatient;
        this.changePatientStatus = changePatientStatus;
    }

    @Override
    public Mono<Patient> register(PatientRegistration registration, String correlationId) {
        return registerPatient.execute(registration, correlationId);
    }

    @Override
    public Mono<Patient> get(UUID patientId) {
        return getPatient.execute(patientId);
    }

    @Override
    public Mono<Patient> changeStatus(UUID patientId, PatientStatus requestedStatus, String correlationId) {
        return changePatientStatus.execute(patientId, requestedStatus, correlationId);
    }
}
