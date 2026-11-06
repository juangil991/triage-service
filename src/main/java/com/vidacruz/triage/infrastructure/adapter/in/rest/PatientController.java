package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.port.in.ChangePatientStatusInputPort;
import com.vidacruz.triage.application.port.in.GetPatientInputPort;
import com.vidacruz.triage.application.port.in.RegisterPatientInputPort;
import com.vidacruz.triage.domain.model.PatientStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/triage/pacientes")
public class PatientController {

    private final RegisterPatientInputPort registerPatient;
    private final GetPatientInputPort getPatient;
    private final ChangePatientStatusInputPort changePatientStatus;
    private final Clock clock;

    public PatientController(
            RegisterPatientInputPort registerPatient,
            GetPatientInputPort getPatient,
            ChangePatientStatusInputPort changePatientStatus,
            Clock clock) {
        this.registerPatient = registerPatient;
        this.getPatient = getPatient;
        this.changePatientStatus = changePatientStatus;
        this.clock = clock;
    }

    @PostMapping
    Mono<ResponseEntity<PatientResponse>> register(
            @RequestBody RegisterPatientRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        return registerPatient.register(
                        request.toRegistration(),
                        correlationId == null ? UUID.randomUUID().toString() : correlationId)
                .map(patient -> ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(patient, clock.instant())));
    }

    @GetMapping("/{id}")
    Mono<PatientResponse> get(@PathVariable UUID id) {
        return getPatient.get(id).map(patient -> PatientResponse.from(patient, clock.instant()));
    }

    @PatchMapping("/{id}/estado")
    Mono<PatientResponse> changeStatus(
            @PathVariable UUID id,
            @RequestBody ChangePatientStatusRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        if (request == null || request.estado() == null) {
            return Mono.error(new IllegalArgumentException("estado es obligatorio"));
        }
        PatientStatus status;
        try {
            status = PatientStatus.valueOf(request.estado());
        } catch (IllegalArgumentException exception) {
            return Mono.error(new IllegalArgumentException("estado debe ser REGISTRADO, EN_CONSULTA o ALTA"));
        }
        String effectiveCorrelationId = correlationId == null ? UUID.randomUUID().toString() : correlationId;
        return changePatientStatus.changeStatus(id, status, effectiveCorrelationId)
                .map(patient -> PatientResponse.from(patient, clock.instant()));
    }
}
