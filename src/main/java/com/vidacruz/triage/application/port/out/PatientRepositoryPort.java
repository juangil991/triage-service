package com.vidacruz.triage.application.port.out;

import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PatientRepositoryPort {

    Mono<Patient> save(Patient patient);

    Mono<Patient> updateStatus(Patient patient, PatientStatus expectedStatus);

    Mono<Patient> findById(UUID id);

    Flux<Patient> findOverdueUnescalated(Instant now, int limit);

    Mono<Patient> markEscalatedIfOverdue(UUID id, Instant now);
}
