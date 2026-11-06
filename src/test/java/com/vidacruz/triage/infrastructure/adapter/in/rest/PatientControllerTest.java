package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.port.in.ChangePatientStatusInputPort;
import com.vidacruz.triage.application.port.in.GetPatientInputPort;
import com.vidacruz.triage.application.port.in.RegisterPatientInputPort;
import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientControllerTest {

    private final RegisterPatientInputPort registerPatient = mock(RegisterPatientInputPort.class);
    private final GetPatientInputPort getPatient = mock(GetPatientInputPort.class);
    private final ChangePatientStatusInputPort changePatientStatus = mock(ChangePatientStatusInputPort.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(
                        new PatientController(registerPatient, getPatient, changePatientStatus, Clock.systemUTC()))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void invalidRegistrationFieldReturnsBadRequestWithFieldName() {
        when(registerPatient.register(any(), anyString()))
                .thenReturn(Mono.error(new InvalidPatientException("spo2", "spo2 debe estar entre 70 y 100")));

        client.post()
                .uri("/triage/pacientes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "nombreCompleto": "Paciente de Prueba",
                          "edad": 40,
                          "frecuenciaCardiacaBpm": 80,
                          "presionSistolicaMmhg": 120,
                          "presionDiastolicaMmhg": 80,
                          "spo2": 69,
                          "temperaturaC": 37.0,
                          "dolor": 2,
                          "gcs": 15,
                          "quejaPrincipal": "consulta"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.campo").isEqualTo("spo2")
                .jsonPath("$.mensaje").isEqualTo("spo2 debe estar entre 70 y 100");
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
