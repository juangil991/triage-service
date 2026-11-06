package com.vidacruz.triage.application.port.out;

import com.vidacruz.triage.domain.model.OutboxEvent;
import com.vidacruz.triage.domain.model.PatientEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OutboxRepositoryPort {

    Mono<Void> save(PatientEvent event);

    Flux<OutboxEvent> findPending(int limit);

    Mono<Void> markPublished(UUID id);

    Mono<Void> recordFailure(UUID id, String error);
}
