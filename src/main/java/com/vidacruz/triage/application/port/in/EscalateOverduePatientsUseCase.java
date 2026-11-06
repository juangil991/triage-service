package com.vidacruz.triage.application.port.in;

import reactor.core.publisher.Mono;

public interface EscalateOverduePatientsUseCase {

    Mono<Void> escalateOverdue();
}
