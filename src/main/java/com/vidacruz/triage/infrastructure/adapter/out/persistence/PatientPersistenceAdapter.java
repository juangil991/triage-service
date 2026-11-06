package com.vidacruz.triage.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.domain.model.ClinicalAudit;
import com.vidacruz.triage.domain.model.OutboxEvent;
import com.vidacruz.triage.domain.model.Patient;
import com.vidacruz.triage.domain.model.PatientEvent;
import com.vidacruz.triage.domain.model.PatientStatus;
import com.vidacruz.triage.domain.model.TriageLevel;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.ClinicalAuditEntity;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.PatientEntity;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.repository.ClinicalAuditR2dbcRepository;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.repository.OutboxEventR2dbcRepository;
import com.vidacruz.triage.infrastructure.adapter.out.persistence.repository.PatientR2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class PatientPersistenceAdapter implements PatientRepositoryPort, ClinicalAuditRepositoryPort, OutboxRepositoryPort {

    private final PatientR2dbcRepository patients;
    private final ClinicalAuditR2dbcRepository audits;
    private final OutboxEventR2dbcRepository outbox;
    private final ObjectMapper objectMapper;

    public PatientPersistenceAdapter(
            PatientR2dbcRepository patients,
            ClinicalAuditR2dbcRepository audits,
            OutboxEventR2dbcRepository outbox,
            ObjectMapper objectMapper) {
        this.patients = patients;
        this.audits = audits;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Patient> save(Patient patient) {
        return patients.save(entity(patient)).map(this::patient);
    }

    @Override
    public Mono<Patient> updateStatus(Patient patient, PatientStatus expectedStatus) {
        return patients.updateStatus(
                        patient.estado().name(),
                        timestamp(patient.fechaInicioEstado()),
                        patient.id(),
                        expectedStatus.name())
                .map(this::patient);
    }

    @Override
    public Mono<Patient> findById(UUID id) {
        return patients.findById(id).map(this::patient);
    }

    @Override
    public Mono<Void> save(ClinicalAudit audit) {
        ClinicalAuditEntity entity = new ClinicalAuditEntity(
                audit.id(),
                audit.patientId(),
                timestamp(audit.timestamp()),
                audit.previousStatus() == null ? null : audit.previousStatus().name(),
                audit.newStatus().name(),
                audit.eventType());
        return audits.save(entity).then();
    }

    @Override
    public Mono<Void> save(PatientEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            return Mono.error(new IllegalStateException(
                    "No se pudo serializar el evento " + event.eventType(), exception));
        }
        return outbox.insertEvent(
                event.eventId(), event.eventType(), payload, timestamp(event.occurredAt())).then();
    }

    @Override
    public Flux<OutboxEvent> findPending(int limit) {
        return outbox.findPending(limit).map(this::outboxEvent);
    }

    @Override
    public Mono<Void> markPublished(UUID id) {
        return outbox.markPublished(id).then();
    }

    @Override
    public Mono<Void> recordFailure(UUID id, String error) {
        String safeError = error.length() > 2000 ? error.substring(0, 2000) : error;
        return outbox.recordFailure(id, safeError).then();
    }

    private PatientEntity entity(Patient patient) {
        return new PatientEntity(
                patient.id(),
                patient.nombreCompleto(),
                patient.edad(),
                patient.frecuenciaCardiacaBpm(),
                patient.presionSistolicaMmhg(),
                patient.presionDiastolicaMmhg(),
                patient.spo2(),
                patient.temperaturaC(),
                patient.dolor(),
                patient.gcs(),
                patient.quejaPrincipal(),
                patient.nivelClasificado().name(),
                patient.slaMinutos(),
                patient.estado().name(),
                patient.escalado(),
                timestamp(patient.fechaRegistro()),
                timestamp(patient.fechaInicioEstado()));
    }

    private Patient patient(PatientEntity entity) {
        return new Patient(
                entity.id(),
                entity.nombreCompleto(),
                entity.edad(),
                entity.frecuenciaCardiacaBpm(),
                entity.presionSistolicaMmhg(),
                entity.presionDiastolicaMmhg(),
                entity.spo2(),
                entity.temperaturaC(),
                entity.dolor(),
                entity.gcs(),
                entity.quejaPrincipal(),
                TriageLevel.valueOf(entity.nivelClasificado()),
                entity.slaMinutos(),
                PatientStatus.valueOf(entity.estado()),
                entity.escalado(),
                instant(entity.fechaRegistro()),
                instant(entity.fechaInicioEstado()));
    }

    private OutboxEvent outboxEvent(OutboxEventEntity entity) {
        return new OutboxEvent(
                entity.id(),
                entity.eventType(),
                entity.payload(),
                instant(entity.createdAt()));
    }

    private Instant instant(OffsetDateTime timestamp) {
        return timestamp.toInstant();
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
