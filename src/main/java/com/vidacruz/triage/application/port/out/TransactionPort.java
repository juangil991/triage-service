package com.vidacruz.triage.application.port.out;

import reactor.core.publisher.Mono;

public interface TransactionPort {

    <T> Mono<T> withinTransaction(Mono<T> work);
}
