package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.port.in.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.port.in.GetPatientUseCase;
import com.vidacruz.triage.application.port.in.RegisterPatientUseCase;
import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientControllerTest {

    private final RegisterPatientUseCase registerPatient = mock(RegisterPatientUseCase.class);
    private final GetPatientUseCase getPatient = mock(GetPatientUseCase.class);
    private final ChangePatientStatusUseCase changePatientStatus = mock(ChangePatientStatusUseCase.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(
                        new PatientController(registerPatient, getPatient, changePatientStatus, Clock.systemUTC()))
                .controllerAdvice(new ApiExceptionHandler())
                .webFilter(new CorrelationIdWebFilter())
                .build();
    }

    @Test
    void invalidRegistrationFieldReturnsBadRequestWithFieldName() {
        when(registerPatient.register(any(), anyString()))
                .thenReturn(Mono.error(new InvalidPatientException("spo2", "spo2 debe estar entre 70 y 100")));

        client.post()
                .uri("/triage/pacientes")
                .header(CorrelationIdWebFilter.HEADER_NAME, "trace-patient-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "nombreCompleto": "Paciente de Prueba",
                          "edad": 40,
                          "frecuenciaCardiaca": 80,
                          "presionSistolica": 120,
                          "presionDiastolica": 80,
                          "spO2": 69,
                          "temperatura": 37.0,
                          "dolor": 2,
                          "gcs": 15,
                          "quejaPrincipal": "consulta"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(CorrelationIdWebFilter.HEADER_NAME, "trace-patient-1")
                .expectBody()
                .jsonPath("$.campo").isEqualTo("spO2")
                .jsonPath("$.mensaje").isEqualTo("spO2 debe estar entre 70 y 100");

        ArgumentCaptor<PatientRegistration> registration = ArgumentCaptor.forClass(PatientRegistration.class);
        verify(registerPatient).register(registration.capture(), anyString());
        assertEquals(new PatientRegistration(
                "Paciente de Prueba",
                40,
                80,
                120,
                80,
                new BigDecimal("69"),
                new BigDecimal("37.0"),
                2,
                15,
                "consulta"), registration.getValue());
    }

    @Test
    void invalidStateTransitionReturnsConflict() {
        UUID patientId = UUID.randomUUID();
        when(changePatientStatus.changeStatus(eq(patientId), eq(PatientStatus.EN_CONSULTA), anyString()))
                .thenReturn(Mono.error(new InvalidPatientTransitionException(
                        PatientStatus.ALTA, PatientStatus.EN_CONSULTA)));

        client.patch()
                .uri("/triage/pacientes/{id}/estado", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"estado\":\"EN_CONSULTA\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("Transición inválida de ALTA a EN_CONSULTA");
    }
}
