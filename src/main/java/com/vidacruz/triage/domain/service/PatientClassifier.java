package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.TriageLevel;

import java.math.BigDecimal;

public class PatientClassifier {

    private static final BigDecimal MIN_SPO2 = new BigDecimal("70");
    private static final BigDecimal MAX_SPO2 = new BigDecimal("100");
    private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("34.0");
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("42.0");

    private final AdministrativeComplaintMatcher complaintMatcher;

    public PatientClassifier(AdministrativeComplaintMatcher complaintMatcher) {
        this.complaintMatcher = complaintMatcher;
    }

    public TriageLevel classify(PatientRegistration registration) {
        validate(registration);

        if (registration.frecuenciaCardiacaBpm() < 40
                || registration.frecuenciaCardiacaBpm() > 120
                || registration.spo2().compareTo(new BigDecimal("90")) < 0
                || registration.presionSistolicaMmhg() < 90
                || registration.gcs() <= 8) {
            return TriageLevel.CRITICO;
        }

        if (between(registration.spo2(), new BigDecimal("90"), new BigDecimal("93"))
                || between(registration.presionSistolicaMmhg(), 90, 100)
                || registration.temperaturaC().compareTo(new BigDecimal("38.5")) >= 0
                || registration.gcs() <= 13) {
            return TriageLevel.URGENTE;
        }

        if (between(registration.frecuenciaCardiacaBpm(), 90, 120)
                || between(registration.temperaturaC(), new BigDecimal("38.0"), new BigDecimal("38.5"), false)
                || registration.dolor() >= 7) {
            return TriageLevel.URGENTE_2;
        }

        return complaintMatcher.matches(registration.quejaPrincipal())
                ? TriageLevel.NO_URGENTE_2
                : TriageLevel.NO_URGENTE;
    }

    private void validate(PatientRegistration registration) {
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
        throw new InvalidPatientException(field, field + " " + message);
    }

    private boolean between(Integer value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private boolean between(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private boolean between(BigDecimal value, BigDecimal minimum, BigDecimal maximum, boolean inclusiveMaximum) {
        return value.compareTo(minimum) >= 0
                && (inclusiveMaximum ? value.compareTo(maximum) <= 0 : value.compareTo(maximum) < 0);
    }
}
