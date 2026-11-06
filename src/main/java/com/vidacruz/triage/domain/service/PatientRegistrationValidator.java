package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.model.PatientRegistration;

import java.math.BigDecimal;

public class PatientRegistrationValidator {

    private static final BigDecimal MIN_SPO2 = new BigDecimal("70");
    private static final BigDecimal MAX_SPO2 = new BigDecimal("100");
    private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("34.0");
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("42.0");

    public void validate(PatientRegistration registration) {
        if (registration == null) {
            throw new InvalidPatientException("body", "el cuerpo de la solicitud es obligatorio");
        }
        required(registration.nombreCompleto(), "nombreCompleto");
        String name = registration.nombreCompleto().trim();
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 3 || nameLength > 100) {
            invalid("nombreCompleto", "debe tener entre 3 y 100 caracteres");
        }
        integer(registration.edad(), 0, 120, "edad");
        integer(registration.frecuenciaCardiacaBpm(), 20, 250, "frecuenciaCardiacaBpm");
        integer(registration.presionSistolicaMmhg(), 60, 260, "presionSistolicaMmhg");
        integer(registration.presionDiastolicaMmhg(), 30, 160, "presionDiastolicaMmhg");
        if (registration.presionDiastolicaMmhg() >= registration.presionSistolicaMmhg()) {
            invalid("presionDiastolicaMmhg", "debe ser menor que presionSistolicaMmhg");
        }
        decimal(registration.spo2(), MIN_SPO2, MAX_SPO2, "spo2");
        decimal(registration.temperaturaC(), MIN_TEMPERATURE, MAX_TEMPERATURE, "temperaturaC");
        integer(registration.dolor(), 0, 10, "dolor");
        integer(registration.gcs(), 3, 15, "gcs");
        required(registration.quejaPrincipal(), "quejaPrincipal");
    }

    private void integer(Integer value, int minimum, int maximum, String field) {
        if (value == null) {
            invalid(field, "es obligatorio");
        }
        if (value < minimum || value > maximum) {
            invalid(field, "debe estar entre " + minimum + " y " + maximum);
        }
    }

    private void decimal(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value == null) {
            invalid(field, "es obligatorio");
        }
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            invalid(field, "debe estar entre " + minimum.stripTrailingZeros().toPlainString()
                    + " y " + maximum.stripTrailingZeros().toPlainString());
        }
    }

    private void required(String value, String field) {
        if (value == null || value.isBlank()) {
            invalid(field, "es obligatorio");
        }
    }

    private void invalid(String field, String message) {
        throw new InvalidPatientException(field, message);
    }
}
