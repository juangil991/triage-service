package com.vidacruz.triage.application.port.in;

import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangePatientStatusInputPort {

    Mono<Patient> changeStatus(UUID patientId, PatientStatus requestedStatus, String correlationId);
}
