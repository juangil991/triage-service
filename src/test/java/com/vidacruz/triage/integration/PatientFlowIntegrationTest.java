package com.vidacruz.triage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PatientFlowIntegrationTest {

    private static final String TOPIC = "triaje.eventos.integration";
    private static final String API_KEY = "integration-triage-key";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("triage_integration")
            .withUsername("triage")
            .withPassword("triage");

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DatabaseClient database;

    @Autowired
    private OutboxRepositoryPort outbox;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.api-key", () -> API_KEY);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName());
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.topic", () -> TOPIC);
        registry.add("app.outbox.poll-interval-ms", () -> "100");
        registry.add("app.triage.escalation.poll-interval-ms", () -> "100");
    }

    @BeforeEach
    void configureApiKey() {
        webClient = webClient.mutate().defaultHeader("X-API-Key", API_KEY).build();
    }

    @BeforeAll
    static void createTopic() throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (Admin admin = Admin.create(properties)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void registrationAndStatusChangesPersistAndPublishEvents() throws Exception {
        UUID correlationId = UUID.randomUUID();
        EntityExchangeResult<JsonNode> registration = webClient.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId.toString())
                .bodyValue("""
                        {
                          "nombreCompleto": "Paciente de Integración",
                          "edad": 35,
                          "frecuenciaCardiaca": 80,
                          "presionSistolica": 120,
                          "presionDiastolica": 80,
                          "spO2": 98,
                          "temperatura": 36.8,
                          "dolor": 2,
                          "gcs": 15,
                          "quejaPrincipal": "Molestia leve"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult();

        JsonNode registered = registration.getResponseBody();
        assertThat(registered).isNotNull();
        UUID patientId = UUID.fromString(registered.path("id").asText());
        assertThat(registered.path("nivel").asText()).isEqualTo("NO_URGENTE");
        assertThat(registered.has("nivelClasificado")).isFalse();
        assertThat(registered.path("frecuenciaCardiacaBpm").asInt()).isEqualTo(80);
        assertThat(registered.path("spo2").asDouble()).isEqualTo(98.0);
        assertThat(registered.path("slaMinutos").asInt()).isEqualTo(60);
        assertThat(registered.path("estado").asText()).isEqualTo("REGISTRADO");

        webClient.get()
                .uri("/triage/pacientes/{id}", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(patientId.toString())
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.estado").isEqualTo("REGISTRADO");

        changeStatus(patientId, "EN_CONSULTA", correlationId);
        changeStatus(patientId, "ALTA", correlationId);

        webClient.get()
                .uri("/triage/pacientes/{id}", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.estado").isEqualTo("ALTA");

        Map<String, ObservedEvent> events = awaitEvents(patientId, 3);
        assertEvent(events.get("paciente_registrado"), patientId, correlationId, "REGISTRADO");
        assertEvent(events.get("paciente_en_consulta"), patientId, correlationId, "EN_CONSULTA");
        assertEvent(events.get("paciente_alta"), patientId, correlationId, "ALTA");

        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(3);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM clinical_audit
                WHERE patient_id = :patient_id
                  AND ((event_type = 'paciente_registrado'
                        AND previous_status IS NULL AND new_status = 'REGISTRADO')
                    OR (event_type = 'paciente_en_consulta'
                        AND previous_status = 'REGISTRADO' AND new_status = 'EN_CONSULTA')
                    OR (event_type = 'paciente_alta'
                        AND previous_status = 'EN_CONSULTA' AND new_status = 'ALTA'))
                """, patientId)).isEqualTo(3);
        awaitPublishedEvents(patientId, 3);
    }

    @Test
    void registrationClassifiesAndPersistsVerificationMatrixE1ThroughE6() {
        List<RegistrationMatrixCase> cases = List.of(
                new RegistrationMatrixCase("E1", 130, 180, 110, 97, 36.8, 8, 15, "Dolor torácico",
                        "CRITICO", 5),
                new RegistrationMatrixCase("E2", 95, 150, 95, 91, 37.0, 4, 15, "Dificultad para respirar",
                        "URGENTE", 10),
                new RegistrationMatrixCase("E3", 88, 130, 85, 98, 38.2, 6, 15, "Fiebre",
                        "URGENTE_2", 30),
                new RegistrationMatrixCase("E4", 72, 120, 80, 99, 36.5, 2, 15, "Herida por cortar",
                        "NO_URGENTE", 60),
                new RegistrationMatrixCase("E5", 70, 115, 75, 99, 36.4, 0, 15, "Renovación de recetario",
                        "NO_URGENTE_2", 120),
                new RegistrationMatrixCase("E6", 35, 80, 50, 85, 35.9, 0, 6, "Inconsciente",
                        "CRITICO", 5));

        for (RegistrationMatrixCase testCase : cases) {
            EntityExchangeResult<JsonNode> registration = webClient.post()
                    .uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(matrixRegistrationBody(testCase))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(JsonNode.class)
                    .returnResult();

            JsonNode registered = registration.getResponseBody();
            assertThat(registered).as(testCase.id()).isNotNull();
            UUID patientId = UUID.fromString(registered.path("id").asText());
            assertThat(registered.path("nivel").asText()).as(testCase.id()).isEqualTo(testCase.expectedLevel());
            assertThat(registered.path("slaMinutos").asInt()).as(testCase.id()).isEqualTo(testCase.expectedSlaMinutes());

            webClient.get()
                    .uri("/triage/pacientes/{id}", patientId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.nivel").isEqualTo(testCase.expectedLevel())
                    .jsonPath("$.slaMinutos").isEqualTo(testCase.expectedSlaMinutes())
                    .jsonPath("$.estado").isEqualTo("REGISTRADO");
        }
    }

    @Test
    void operationalReportGroupsRegistrationsAndCalculatesWaitStatisticsInSql() {
        insertReportPatient(
                UUID.randomUUID(), "CRITICO", "EN_CONSULTA",
                OffsetDateTime.parse("2025-01-15T10:15:00Z"),
                OffsetDateTime.parse("2025-01-15T10:25:00Z"));
        insertReportPatient(
                UUID.randomUUID(), "URGENTE", "EN_CONSULTA",
                OffsetDateTime.parse("2025-01-15T10:45:00Z"),
                OffsetDateTime.parse("2025-01-15T11:05:00Z"));
        insertReportPatient(
                UUID.randomUUID(), "NO_URGENTE", "ALTA",
                OffsetDateTime.parse("2025-01-15T10:55:00Z"), null);
        insertReportPatient(
                UUID.randomUUID(), "URGENTE_2", "EN_CONSULTA",
                OffsetDateTime.parse("2025-01-15T11:20:00Z"),
                OffsetDateTime.parse("2025-01-15T11:50:00Z"));

        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/triage/reportes/operacion")
                        .queryParam("desde", "2025-01-15T10:00:00Z")
                        .queryParam("hasta", "2025-01-15T12:00:00Z")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].hora").isEqualTo("2025-01-15T10:00:00Z")
                .jsonPath("$[0].totalPacientesRegistrados").isEqualTo(3)
                .jsonPath("$[0].critico").isEqualTo(1)
                .jsonPath("$[0].urgente").isEqualTo(1)
                .jsonPath("$[0].noUrgente").isEqualTo(1)
                .jsonPath("$[0].tiempoPromedioEsperaMinutos").isEqualTo(15.0)
                .jsonPath("$[0].p95TiempoEsperaMinutos").isEqualTo(20.0)
                .jsonPath("$[1].hora").isEqualTo("2025-01-15T11:00:00Z")
                .jsonPath("$[1].totalPacientesRegistrados").isEqualTo(1)
                .jsonPath("$[1].urgente2").isEqualTo(1)
                .jsonPath("$[1].tiempoPromedioEsperaMinutos").isEqualTo(30.0)
                .jsonPath("$[1].p95TiempoEsperaMinutos").isEqualTo(30.0);
    }

    @Test
    void operationalReportUsesNearestRankForP95InsteadOfMaximum() {
        OffsetDateTime registeredAt = OffsetDateTime.parse("2025-02-01T10:00:00Z");
        for (int waitMinutes = 1; waitMinutes <= 20; waitMinutes++) {
            insertReportPatient(
                    UUID.randomUUID(),
                    "NO_URGENTE",
                    "EN_CONSULTA",
                    registeredAt,
                    registeredAt.plusMinutes(waitMinutes));
        }

        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/triage/reportes/operacion")
                        .queryParam("desde", "2025-02-01T10:00:00Z")
                        .queryParam("hasta", "2025-02-01T11:00:00Z")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].totalPacientesRegistrados").isEqualTo(20)
                .jsonPath("$[0].tiempoPromedioEsperaMinutos").isEqualTo(10.5)
                .jsonPath("$[0].p95TiempoEsperaMinutos").isEqualTo(19.0);
    }

    @Test
    void operationalReportRejectsAnInvalidDateRange() {
        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/triage/reportes/operacion")
                        .queryParam("desde", "2025-01-15T12:00:00Z")
                        .queryParam("hasta", "2025-01-15T10:00:00Z")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("desde debe ser anterior a hasta");
    }

    @Test
    void overdueRegisteredPatientIsEscalatedOnceAndPublishesEvent() throws Exception {
        UUID patientId = registerPatient(UUID.randomUUID());
        database.sql("""
                UPDATE triage_patient
                SET fecha_registro = CURRENT_TIMESTAMP - INTERVAL '2 hours'
                WHERE id = :patient_id
                """)
                .bind("patient_id", patientId)
                .then()
                .block(Duration.ofSeconds(5));

        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM triage_patient
                WHERE id = :patient_id
                  AND estado = 'REGISTRADO'
                  AND escalado = FALSE
                  AND fecha_registro + (sla_minutos * INTERVAL '1 minute') <= CURRENT_TIMESTAMP
                """, patientId)).isEqualTo(1);
        awaitEscalated(patientId);

        webClient.get()
                .uri("/triage/pacientes/{id}", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.escalado").isEqualTo(true);

        Map<String, ObservedEvent> events = awaitEvents(patientId, 2);
        ObservedEvent escalated = events.get("paciente_escalado");
        assertThat(escalated).isNotNull();
        assertThat(escalated.value().path("correlationId").asText()).isNotBlank();
        assertThat(escalated.value().path("payload").path("escalado").asBoolean()).isTrue();
        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(2);
        awaitPublishedEvents(patientId, 2);

        Thread.sleep(300);
        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(2);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total FROM outbox_event
                WHERE (payload ->> 'patientId')::uuid = :patient_id
                """, patientId)).isEqualTo(2);
    }

    @Test
    void patientsAlreadyInConsultationOrDischargedAreNeverEscalated() throws InterruptedException {
        UUID inConsultation = registerPatient(UUID.randomUUID());
        changeStatus(inConsultation, "EN_CONSULTA", UUID.randomUUID());
        makeRegistrationOverdue(inConsultation);

        UUID discharged = registerPatient(UUID.randomUUID());
        changeStatus(discharged, "EN_CONSULTA", UUID.randomUUID());
        changeStatus(discharged, "ALTA", UUID.randomUUID());
        makeRegistrationOverdue(discharged);

        Thread.sleep(300);

        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM triage_patient
                WHERE id = :patient_id
                  AND estado IN ('EN_CONSULTA', 'ALTA')
                  AND escalado = FALSE
                """, inConsultation)).isEqualTo(1);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM triage_patient
                WHERE id = :patient_id
                  AND estado = 'ALTA'
                  AND escalado = FALSE
                """, discharged)).isEqualTo(1);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM clinical_audit
                WHERE patient_id = :patient_id AND event_type = 'paciente_escalado'
                """, inConsultation)).isZero();
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total
                FROM clinical_audit
                WHERE patient_id = :patient_id AND event_type = 'paciente_escalado'
                """, discharged)).isZero();
    }

    @Test
    void registrationValidationReturnsFieldSpecificErrorsWithoutWrites() {
        long patientsBefore = countRows("triage_patient");
        long auditsBefore = countRows("clinical_audit");
        long eventsBefore = countRows("outbox_event");
        List<InvalidRegistration> invalidRequests = List.of(
                new InvalidRegistration(validRegistrationBody().replace("\"spO2\": 98", "\"spO2\": 69"), "spO2"),
                new InvalidRegistration(validRegistrationBody().replace(
                        "\"nombreCompleto\": \"Paciente de Integración\"", "\"nombreCompleto\": \"  \""),
                        "nombreCompleto"),
                new InvalidRegistration(validRegistrationBody().replace("\"edad\": 35", "\"edad\": null"), "edad"),
                new InvalidRegistration(validRegistrationBody().replace(
                        "\"quejaPrincipal\": \"Molestia leve\"", "\"quejaPrincipal\": \"  \""),
                        "quejaPrincipal"));

        for (InvalidRegistration invalid : invalidRequests) {
            webClient.post()
                    .uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalid.body())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.campo").isEqualTo(invalid.field());
        }

        assertThat(countRows("triage_patient")).isEqualTo(patientsBefore);
        assertThat(countRows("clinical_audit")).isEqualTo(auditsBefore);
        assertThat(countRows("outbox_event")).isEqualTo(eventsBefore);
    }

    @Test
    void malformedRequestsAndUnknownPatientsReturnClientErrors() {
        webClient.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{malformed")
                .exchange()
                .expectStatus().isBadRequest();

        UUID missingPatientId = UUID.randomUUID();
        webClient.get()
                .uri("/triage/pacientes/{id}", missingPatientId)
                .exchange()
                .expectStatus().isNotFound();

        webClient.patch()
                .uri("/triage/pacientes/{id}/estado", missingPatientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("estado", "EN_CONSULTA"))
                .exchange()
                .expectStatus().isNotFound();

        webClient.patch()
                .uri("/triage/pacientes/{id}/estado", missingPatientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("estado", "INVALIDO"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void directDischargeReturnsConflictWithoutAuditOrEventWrites() {
        UUID patientId = registerPatient(UUID.randomUUID());
        long auditsBefore = countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId);
        long outboxBefore = countForPatient("""
                SELECT COUNT(*) AS total FROM outbox_event
                WHERE (payload ->> 'patientId')::uuid = :patient_id
                """, patientId);

        webClient.patch()
                .uri("/triage/pacientes/{id}/estado", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("estado", "ALTA"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("Transición inválida de REGISTRADO a ALTA");

        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(auditsBefore);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total FROM outbox_event
                WHERE (payload ->> 'patientId')::uuid = :patient_id
                """, patientId)).isEqualTo(outboxBefore);
    }

    @Test
    void concurrentStatusChangesPersistOnlyTheWinningTransition() {
        UUID patientId = registerPatient(UUID.randomUUID());
        CompletableFuture<HttpStatusCode> first = CompletableFuture.supplyAsync(
                () -> changeStatusAndReturnStatus(patientId, "EN_CONSULTA"));
        CompletableFuture<HttpStatusCode> second = CompletableFuture.supplyAsync(
                () -> changeStatusAndReturnStatus(patientId, "EN_CONSULTA"));

        assertThat(List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(2);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total FROM outbox_event
                WHERE (payload ->> 'patientId')::uuid = :patient_id
                """, patientId)).isEqualTo(2);
    }

    @Test
    void outboxFailureRollsBackPatientAndAuditWrites() {
        String correlationId = "rollback-" + UUID.randomUUID();
        long patientsBefore = countRows("triage_patient");
        long auditsBefore = countRows("clinical_audit");

        installOutboxFailureTrigger(correlationId);
        try {
            webClient.post()
                    .uri("/triage/pacientes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(validRegistrationBody())
                    .exchange()
                    .expectStatus().is5xxServerError();
        } finally {
            removeOutboxFailureTrigger();
        }

        assertThat(countRows("triage_patient")).isEqualTo(patientsBefore);
        assertThat(countRows("clinical_audit")).isEqualTo(auditsBefore);
        assertThat(countForCorrelation(correlationId)).isZero();
    }

    @Test
    void outboxFailureRollsBackStatusAndAuditWrites() {
        UUID patientId = registerPatient(UUID.randomUUID());
        String correlationId = "status-rollback-" + UUID.randomUUID();
        installOutboxFailureTrigger(correlationId);
        try {
            webClient.patch()
                    .uri("/triage/pacientes/{id}/estado", patientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(Map.of("estado", "EN_CONSULTA"))
                    .exchange()
                    .expectStatus().is5xxServerError();
        } finally {
            removeOutboxFailureTrigger();
        }

        webClient.get()
                .uri("/triage/pacientes/{id}", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("REGISTRADO");
        assertThat(countForPatient(
                "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :patient_id",
                patientId)).isEqualTo(1);
        assertThat(countForPatient("""
                SELECT COUNT(*) AS total FROM outbox_event
                WHERE (payload ->> 'patientId')::uuid = :patient_id
                """, patientId)).isEqualTo(1);
        assertThat(countForCorrelation(correlationId)).isZero();
    }

    @Test
    void outboxFailureRecordingAppliesBackoffCapAndErrorLimit() {
        UUID retryEvent = insertDelayedRetryEvent(8);
        String longError = "x".repeat(2200);
        outbox.recordFailure(retryEvent, longError).block(Duration.ofSeconds(5));

        RetryState retryState = retryState(retryEvent);
        assertThat(retryState.sendAttempts()).isEqualTo(9);
        assertThat(retryState.lastError()).hasSize(2000);
        assertThat(retryState.publishedAt()).isNull();
        assertThat(retryState.retryDelaySeconds()).isBetween(250L, 260L);

        UUID cappedEvent = insertDelayedRetryEvent(100);
        outbox.recordFailure(cappedEvent, "simulated failure").block(Duration.ofSeconds(5));
        assertThat(retryState(cappedEvent).retryDelaySeconds()).isBetween(295L, 300L);
    }

    @Test
    void missingCorrelationHeaderStillProducesCorrelatedEvent() throws Exception {
        UUID patientId = registerPatientWithoutCorrelationHeader();
        ObservedEvent event = awaitEvents(patientId, 1).get("paciente_registrado");

        assertThat(event).isNotNull();
        assertThat(event.value().path("correlationId").asText()).isNotBlank();
        assertThat(event.key()).isEqualTo(event.value().path("eventId").asText());
    }

    @Test
    void delayedOutboxRetriesDoNotBlockNewEvents() throws InterruptedException {
        database.sql("""
                INSERT INTO outbox_event (
                    id, event_type, payload, created_at, next_attempt_at, send_attempts, last_error
                )
                SELECT md5('delayed-retry-' || attempt)::uuid,
                       'integration_backoff_test',
                       '{"marker":"delayed"}'::jsonb,
                       CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                       CURRENT_TIMESTAMP + INTERVAL '1 hour',
                       8,
                       'simulated failure'
                FROM generate_series(1, 100) AS attempt
                """)
                .then()
                .block(Duration.ofSeconds(5));

        UUID eligibleId = UUID.randomUUID();
        database.sql("""
                INSERT INTO outbox_event (
                    id, event_type, payload, created_at, next_attempt_at
                )
                VALUES (:id, 'integration_backoff_test', '{"marker":"eligible"}'::jsonb,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .bind("id", eligibleId)
                .then()
                .block(Duration.ofSeconds(5));

        awaitPublishedEvent(eligibleId);

        assertThat(countPublishedForMarker("delayed")).isZero();
    }

    private void changeStatus(UUID patientId, String status, UUID correlationId) {
        webClient.patch()
                .uri("/triage/pacientes/{id}/estado", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId.toString())
                .bodyValue(Map.of("estado", status))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.estado").isEqualTo(status);
    }

    private Map<String, ObservedEvent> awaitEvents(UUID patientId, int expectedCount) throws Exception {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "triage-integration-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        Map<String, ObservedEvent> events = new HashMap<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            while (events.size() < expectedCount && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode event = objectMapper.readTree(record.value());
                    if (patientId.toString().equals(event.path("patientId").asText())) {
                        events.put(event.path("eventType").asText(), new ObservedEvent(record.key(), event));
                    }
                }
            }
        }
        return events;
    }

    private void assertEvent(
            ObservedEvent observed,
            UUID patientId,
            UUID correlationId,
            String expectedStatus) {
        assertThat(observed).isNotNull();
        JsonNode event = observed.value();
        assertThat(observed.key()).isEqualTo(event.path("eventId").asText());
        assertThat(event.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.path("correlationId").asText()).isEqualTo(correlationId.toString());
        assertThat(event.path("patientId").asText()).isEqualTo(patientId.toString());
        assertThat(event.path("payload").path("triageLevel").asText()).isEqualTo("NO_URGENTE");
        assertThat(event.path("payload").path("status").asText()).isEqualTo(expectedStatus);
    }

    private UUID registerPatient(UUID correlationId) {
        EntityExchangeResult<JsonNode> registration = webClient.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId.toString())
                .bodyValue(validRegistrationBody())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult();
        return UUID.fromString(registration.getResponseBody().path("id").asText());
    }

    private UUID registerPatientWithoutCorrelationHeader() throws Exception {
        EntityExchangeResult<JsonNode> registration = webClient.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRegistrationBody())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult();
        return UUID.fromString(registration.getResponseBody().path("id").asText());
    }

    private HttpStatusCode changeStatusAndReturnStatus(UUID patientId, String status) {
        return webClient.patch()
                .uri("/triage/pacientes/{id}/estado", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("estado", status))
                .exchange()
                .returnResult(Void.class)
                .getStatus();
    }

    private String validRegistrationBody() {
        return """
                {
                  "nombreCompleto": "Paciente de Integración",
                  "edad": 35,
                  "frecuenciaCardiaca": 80,
                  "presionSistolica": 120,
                  "presionDiastolica": 80,
                  "spO2": 98,
                  "temperatura": 36.8,
                  "dolor": 2,
                  "gcs": 15,
                  "quejaPrincipal": "Molestia leve"
                }
                """;
    }

    private String matrixRegistrationBody(RegistrationMatrixCase testCase) {
        return """
                {
                  "nombreCompleto": "Paciente %s",
                  "edad": 35,
                  "frecuenciaCardiaca": %d,
                  "presionSistolica": %d,
                  "presionDiastolica": %d,
                  "spO2": %s,
                  "temperatura": %s,
                  "dolor": %d,
                  "gcs": %d,
                  "quejaPrincipal": "%s"
                }
                """.formatted(
                testCase.id(),
                testCase.heartRate(),
                testCase.systolicPressure(),
                testCase.diastolicPressure(),
                testCase.spo2(),
                testCase.temperature(),
                testCase.pain(),
                testCase.gcs(),
                testCase.complaint());
    }

    private UUID insertDelayedRetryEvent(int attempts) {
        UUID eventId = UUID.randomUUID();
        database.sql("""
                INSERT INTO outbox_event (
                    id, event_type, payload, created_at, next_attempt_at, send_attempts
                )
                VALUES (:id, 'integration_retry_state_test', '{}',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour', :attempts)
                """)
                .bind("id", eventId)
                .bind("attempts", attempts)
                .then()
                .block(Duration.ofSeconds(5));
        return eventId;
    }

    private RetryState retryState(UUID eventId) {
        return database.sql("""
                        SELECT send_attempts, last_error, published_at,
                               FLOOR(EXTRACT(EPOCH FROM (next_attempt_at - CURRENT_TIMESTAMP)))::bigint
                                   AS retry_delay_seconds
                        FROM outbox_event
                        WHERE id = :id
                        """)
                .bind("id", eventId)
                .map((row, metadata) -> new RetryState(
                        row.get("send_attempts", Integer.class),
                        row.get("last_error", String.class),
                        row.get("published_at", OffsetDateTime.class),
                        row.get("retry_delay_seconds", Long.class)))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private void awaitPublishedEvents(UUID patientId, long expectedCount) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        long publishedCount = 0;
        do {
            publishedCount = countForPatient("""
                    SELECT COUNT(*) AS total
                    FROM outbox_event
                    WHERE (payload ->> 'patientId')::uuid = :patient_id
                      AND published_at IS NOT NULL
                    """, patientId);
            if (publishedCount == expectedCount) {
                break;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);

        assertThat(publishedCount).isEqualTo(expectedCount);
    }

    private void awaitPublishedEvent(UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        long publishedCount = 0;
        do {
            publishedCount = countForEvent(
                    "SELECT COUNT(*) AS total FROM outbox_event WHERE id = :event_id AND published_at IS NOT NULL",
                    eventId);
            if (publishedCount == 1) {
                break;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);

        assertThat(publishedCount).isEqualTo(1);
    }

    private void awaitEscalated(UUID patientId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        long escalated = 0;
        do {
            escalated = countForPatient("""
                    SELECT COUNT(*) AS total
                    FROM triage_patient
                    WHERE id = :patient_id AND escalado = TRUE
                    """, patientId);
            if (escalated == 1) {
                break;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(escalated).isEqualTo(1);
    }

    private void makeRegistrationOverdue(UUID patientId) {
        database.sql("""
                UPDATE triage_patient
                SET fecha_registro = CURRENT_TIMESTAMP - INTERVAL '2 hours'
                WHERE id = :patient_id
                """)
                .bind("patient_id", patientId)
                .then()
                .block(Duration.ofSeconds(5));
    }

    private long countPublishedForMarker(String marker) {
        return database.sql("""
                        SELECT COUNT(*) AS total
                        FROM outbox_event
                        WHERE payload ->> 'marker' = :marker
                          AND published_at IS NOT NULL
                        """)
                .bind("marker", marker)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private long countForEvent(String sql, UUID eventId) {
        return database.sql(sql)
                .bind("event_id", eventId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private long countRows(String table) {
        return database.sql("SELECT COUNT(*) AS total FROM " + table)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private long countForCorrelation(String correlationId) {
        return database.sql("""
                        SELECT COUNT(*) AS total FROM outbox_event
                        WHERE payload ->> 'correlationId' = :correlation_id
                        """)
                .bind("correlation_id", correlationId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private long countForPatient(String sql, UUID patientId) {
        return database.sql(sql)
                .bind("patient_id", patientId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }

    private void executeSql(String sql) {
        database.sql(sql).then().block(Duration.ofSeconds(5));
    }

    private void insertReportPatient(
            UUID id,
            String level,
            String status,
            OffsetDateTime registeredAt,
            OffsetDateTime consultationAt) {
        database.sql("""
                INSERT INTO triage_patient (
                    id, nombre_completo, edad, frecuencia_cardiaca_bpm,
                    presion_sistolica_mmhg, presion_diastolica_mmhg, spo2,
                    temperatura_c, dolor, gcs, queja_principal, nivel_clasificado,
                    sla_minutos, estado, escalado, fecha_registro, fecha_inicio_estado
                )
                VALUES (
                    :id, 'Paciente de Reporte', 35, 80, 120, 80, 98,
                    36.8, 2, 15, 'Prueba de reporte', :level,
                    60, :status, FALSE, :registered_at, :state_started_at
                )
                """)
                .bind("id", id)
                .bind("level", level)
                .bind("status", status)
                .bind("registered_at", registeredAt)
                .bind("state_started_at", consultationAt == null ? registeredAt : consultationAt)
                .then()
                .block(Duration.ofSeconds(5));

        if (consultationAt != null) {
            database.sql("""
                    INSERT INTO clinical_audit (
                        id, patient_id, occurred_at, previous_status, new_status, event_type
                    )
                    VALUES (:audit_id, :patient_id, :occurred_at,
                            'REGISTRADO', 'EN_CONSULTA', 'paciente_en_consulta')
                    """)
                    .bind("audit_id", UUID.randomUUID())
                    .bind("patient_id", id)
                    .bind("occurred_at", consultationAt)
                    .then()
                    .block(Duration.ofSeconds(5));
        }
    }

    private void installOutboxFailureTrigger(String correlationId) {
        executeSql("""
                CREATE OR REPLACE FUNCTION reject_test_outbox_event() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.payload ->> 'correlationId' = '%s' THEN
                        RAISE EXCEPTION 'simulated outbox persistence failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(correlationId));
        executeSql("""
                CREATE TRIGGER reject_test_outbox_event
                BEFORE INSERT ON outbox_event
                FOR EACH ROW EXECUTE FUNCTION reject_test_outbox_event()
                """);
    }

    private void removeOutboxFailureTrigger() {
        executeSql("DROP TRIGGER reject_test_outbox_event ON outbox_event");
        executeSql("DROP FUNCTION reject_test_outbox_event()");
    }

    private record InvalidRegistration(String body, String field) {
    }

    private record RegistrationMatrixCase(
            String id,
            int heartRate,
            int systolicPressure,
            int diastolicPressure,
            int spo2,
            double temperature,
            int pain,
            int gcs,
            String complaint,
            String expectedLevel,
            int expectedSlaMinutes) {
    }

    private record ObservedEvent(String key, JsonNode value) {
    }

    private record RetryState(
            Integer sendAttempts,
            String lastError,
            OffsetDateTime publishedAt,
            Long retryDelaySeconds) {
    }
}
