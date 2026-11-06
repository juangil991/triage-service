package com.vidacruz.triage.domain.exception;

import com.vidacruz.triage.domain.model.PatientStatus;

public class InvalidPatientTransitionException extends RuntimeException {

    public InvalidPatientTransitionException(PatientStatus current, PatientStatus requested) {
        super("Transición inválida de " + current + " a " + requested);
    }
}
