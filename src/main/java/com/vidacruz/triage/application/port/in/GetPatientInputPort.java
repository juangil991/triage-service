package com.vidacruz.triage.application.port.in;

import com.vidacruz.triage.domain.model.Patient;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetPatientInputPort {

    Mono<Patient> get(UUID patientId);
}
