package com.vidacruz.triage.infrastructure.adapter.out.kafka;

import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.domain.model.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepositoryPort outbox;
    private final KafkaSender<String, String> sender;
    private final String topic;
    private final AtomicBoolean publishing = new AtomicBoolean();

    public OutboxRelay(
            OutboxRepositoryPort outbox,
            KafkaSender<String, String> sender,
            @Value("${app.kafka.topic}") String topic) {
        this.outbox = outbox;
        this.sender = sender;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        if (!publishing.compareAndSet(false, true)) {
            return;
        }
        outbox.findPending(100)
                .concatMap(this::publish)
                .then()
                .doFinally(signal -> publishing.set(false))
                .subscribe(
                        ignored -> { },
                        error -> logger.error("Falló la lectura de eventos pendientes del Outbox", error));
    }

    private Mono<Void> publish(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic, event.id().toString(), event.payload());
        return sender.send(Mono.just(SenderRecord.create(record, event.id())))
                .single()
                .flatMap(result -> result.exception() == null
                        ? outbox.markPublished(event.id())
                        : Mono.error(result.exception()))
                .onErrorResume(error -> {
                    logger.error("No se pudo publicar el evento Outbox {}", event.id(), error);
                    return outbox.recordFailure(event.id(), error.toString())
                            .doOnError(recordError -> logger.error(
                                    "No se pudo registrar el fallo del evento Outbox {}", event.id(), recordError))
                            .onErrorResume(recordError -> Mono.empty());
                });
    }
}
