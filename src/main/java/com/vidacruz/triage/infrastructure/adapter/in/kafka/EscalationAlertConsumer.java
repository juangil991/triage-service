package com.vidacruz.triage.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.application.port.out.MedicalTeamAlertPort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.alerts.webhook.enabled", havingValue = "true")
public class EscalationAlertConsumer {

    private static final Logger logger = LoggerFactory.getLogger(EscalationAlertConsumer.class);
    private static final String ESCALATION_EVENT = "paciente_escalado";

    private final KafkaReceiver<String, String> receiver;
    private final ObjectMapper objectMapper;
    private final MedicalTeamAlertPort alertPort;
    private Disposable subscription;

    public EscalationAlertConsumer(
            KafkaReceiver<String, String> escalationAlertKafkaReceiver,
            ObjectMapper objectMapper,
            MedicalTeamAlertPort alertPort) {
        this.receiver = escalationAlertKafkaReceiver;
        this.objectMapper = objectMapper;
        this.alertPort = alertPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscription = receiver.receive()
                .concatMap(this::processRecord)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(retry -> logger.warn(
                                "Se reintentará la entrega de una alerta de escalamiento",
                                retry.failure())))
                .subscribe(
                        ignored -> { },
                        error -> logger.error("El consumidor de alertas de escalamiento terminó", error));
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    Mono<Void> processRecord(ReceiverRecord<String, String> record) {
        return notifyIfEscalated(record.value())
                .then(Mono.defer(() -> record.receiverOffset().commit()))
                .doOnError(error -> logger.error(
                        "No se pudo procesar la alerta de escalamiento en offset {}",
                        record.receiverOffset().offset(),
                        error));
    }

    private Mono<Void> notifyIfEscalated(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, EscalationAlertEventMessage.class))
                .flatMap(message -> ESCALATION_EVENT.equals(message.eventType())
                        ? alertPort.send(message.toAlert())
                        : Mono.empty());
    }
}
