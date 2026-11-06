package com.vidacruz.triage.application.port.in;

import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientRegistration;
import reactor.core.publisher.Mono;

public interface RegisterPatientUseCase {

    Mono<Patient> register(PatientRegistration registration, String correlationId);
}
