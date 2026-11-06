package com.vidacruz.triage.infrastructure.adapter.out.kafka;

import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.domain.model.OutboxEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void failedKafkaSendIsRecordedWithoutMarkingEventPublished() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "paciente_registrado", "{}", Instant.now());
        OutboxRepositoryPort outbox = mock(OutboxRepositoryPort.class);
        KafkaSender<String, String> sender = mock(KafkaSender.class);
        SenderResult<UUID> failedResult = mock(SenderResult.class);
        CountDownLatch failureRecorded = new CountDownLatch(1);

        when(outbox.findPending(100)).thenReturn(Flux.just(event));
        when(sender.<UUID>send(any())).thenReturn(Flux.just(failedResult));
        when(failedResult.exception()).thenReturn(new IllegalStateException("Kafka unavailable"));
        when(outbox.recordFailure(eq(eventId), anyString()))
                .thenReturn(Mono.fromRunnable(failureRecorded::countDown));

        new OutboxRelay(outbox, sender, "triaje.eventos.integration").publishPending();

        assertThat(failureRecorded.await(5, TimeUnit.SECONDS)).isTrue();
        verify(outbox).recordFailure(eq(eventId), org.mockito.ArgumentMatchers.contains("Kafka unavailable"));
        verify(outbox, never()).markPublished(eventId);
    }
}
