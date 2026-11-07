package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.exception.PatientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidPatientException.class)
    ResponseEntity<Map<String, String>> invalidPatient(InvalidPatientException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "campo", requestFieldName(exception.field()),
                "mensaje", requestMessage(exception.getMessage())));
    }

    private String requestFieldName(String field) {
        return switch (field) {
            case "frecuenciaCardiacaBpm" -> "frecuenciaCardiaca";
            case "presionSistolicaMmhg" -> "presionSistolica";
            case "presionDiastolicaMmhg" -> "presionDiastolica";
            case "spo2" -> "spO2";
            case "temperaturaC" -> "temperatura";
            default -> field;
        };
    }

    private String requestMessage(String message) {
        return message
                .replace("frecuenciaCardiacaBpm", "frecuenciaCardiaca")
                .replace("presionSistolicaMmhg", "presionSistolica")
                .replace("presionDiastolicaMmhg", "presionDiastolica")
                .replace("spo2", "spO2")
                .replace("temperaturaC", "temperatura");
    }

    @ExceptionHandler(InvalidPatientTransitionException.class)
    ResponseEntity<Map<String, String>> invalidTransition(InvalidPatientTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", exception.getMessage()));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    ResponseEntity<Map<String, String>> patientNotFound(PatientNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", exception.getMessage()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<Map<String, String>> invalidRequest(ServerWebInputException exception) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Solicitud inválida: " + exception.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", exception.getMessage()));
    }
}
