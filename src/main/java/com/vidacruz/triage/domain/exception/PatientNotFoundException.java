package com.vidacruz.triage.domain.exception;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(UUID id) {
        super("No existe un paciente con id " + id);
    }
}
