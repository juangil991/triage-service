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
                "campo", exception.field(),
                "mensaje", exception.getMessage()));
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
