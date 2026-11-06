package com.vidacruz.triage.domain.exception;

public class InvalidPatientException extends RuntimeException {

    private final String field;

    public InvalidPatientException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
