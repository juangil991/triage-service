package com.vidacruz.triage.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidacruz.triage.application.port.out.MedicalTeamAlertPort;
import com.vidacruz.triage.domain.model.EscalationAlert;
import com.vidacruz.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalationAlertConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("c0a80123-4567-489a-9abc-123456789abc");
    private static final UUID PATIENT_ID = UUID.fromString("a0a80123-4567-489a-9abc-123456789abc");
    private static final Instant ESCALATED_AT = Instant.parse("2026-11-06T14:00:00Z");

    @SuppressWarnings("unchecked")
    private final KafkaReceiver<String, String> receiver = mock(KafkaReceiver.class);
    private final MedicalTeamAlertPort alertPort = mock(MedicalTeamAlertPort.class);
    private final EscalationAlertConsumer consumer = new EscalationAlertConsumer(
            receiver,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            alertPort);

    @Test
    void escalationEventSendsAlertAndCommitsOnlyAfterDelivery() {
        ReceiverOffset offset = mock(ReceiverOffset.class);
        ReceiverRecord<String, String> record = record(offset, escalationEvent());
        when(alertPort.send(any(EscalationAlert.class))).thenReturn(Mono.empty());

        StepVerifier.create(consumer.processRecord(record)).verifyComplete();

        verify(alertPort).send(argThat(alert -> alert.eventId().equals(EVENT_ID)
                && alert.eventType().equals("paciente_escalado")
                && alert.patientId().equals(PATIENT_ID)
                && alert.triageLevel() == TriageLevel.CRITICO
                && alert.escalatedAt().equals(ESCALATED_AT)
                && alert.slaMinutes() == 5
                && alert.correlationId().equals("req-123")));
        verify(offset).commit();
    }

    @Test
    void unrelatedEventIsCommittedWithoutSendingAnAlert() {
        ReceiverOffset offset = mock(ReceiverOffset.class);
        ReceiverRecord<String, String> record = record(offset, """
                {"eventId":"%s","eventType":"paciente_registrado"}
                """.formatted(EVENT_ID));

        StepVerifier.create(consumer.processRecord(record)).verifyComplete();

        verify(alertPort, never()).send(any());
        verify(offset).commit();
    }

    @Test
    void failedWebhookDeliveryDoesNotCommitTheEvent() {
        ReceiverOffset offset = mock(ReceiverOffset.class);
        ReceiverRecord<String, String> record = record(offset, escalationEvent());
        when(alertPort.send(any(EscalationAlert.class)))
                .thenReturn(Mono.error(new IllegalStateException("webhook unavailable")));

        StepVerifier.create(consumer.processRecord(record))
                .expectErrorMessage("webhook unavailable")
                .verify();

        verify(offset, never()).commit();
    }

    private ReceiverRecord<String, String> record(ReceiverOffset offset, String json) {
        @SuppressWarnings("unchecked")
        ReceiverRecord<String, String> record = mock(ReceiverRecord.class);
        when(record.value()).thenReturn(json);
        when(record.receiverOffset()).thenReturn(offset);
        when(offset.offset()).thenReturn(12L);
        when(offset.commit()).thenReturn(Mono.empty());
        return record;
    }

    private String escalationEvent() {
        return """
                {
                  "eventId": "%s",
                  "eventType": "paciente_escalado",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "correlationId": "req-123",
                  "patientId": "%s",
                  "payload": {
                    "triageLevel": "CRITICO",
                    "status": "REGISTRADO",
                    "registeredAt": "2026-11-06T13:55:00Z",
                    "slaMinutes": 5,
                    "escalado": true
                  }
                }
                """.formatted(EVENT_ID, ESCALATED_AT, PATIENT_ID);
    }
}
