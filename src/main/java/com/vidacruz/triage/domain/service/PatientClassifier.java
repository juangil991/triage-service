package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.TriageLevel;

import java.math.BigDecimal;
import java.util.Objects;

public class PatientClassifier {

    private static final BigDecimal CRITICAL_SPO2_THRESHOLD = new BigDecimal("90");
    private static final BigDecimal URGENT_SPO2_MIN = new BigDecimal("90");
    private static final BigDecimal URGENT_SPO2_MAX = new BigDecimal("93");
    private static final BigDecimal URGENT_TEMPERATURE_THRESHOLD = new BigDecimal("38.5");
    private static final BigDecimal URGENT_2_TEMPERATURE_MIN = new BigDecimal("38.0");
    private static final BigDecimal URGENT_2_TEMPERATURE_MAX = new BigDecimal("38.5");

    private static final int CRITICAL_HEART_RATE_MIN = 40;
    private static final int CRITICAL_HEART_RATE_MAX = 120;
    private static final int CRITICAL_SYSTOLIC_PRESSURE_MIN = 90;
    private static final int CRITICAL_GCS_THRESHOLD = 8;
    private static final int URGENT_SYSTOLIC_PRESSURE_MIN = 90;
    private static final int URGENT_SYSTOLIC_PRESSURE_MAX = 100;
    private static final int URGENT_GCS_THRESHOLD = 13;
    private static final int URGENT_2_HEART_RATE_MIN = 90;
    private static final int URGENT_2_HEART_RATE_MAX = 120;
    private static final int URGENT_2_PAIN_THRESHOLD = 7;

    private final PatientRegistrationValidator registrationValidator;
    private final AdministrativeComplaintMatcher complaintMatcher;

    public PatientClassifier(
            PatientRegistrationValidator registrationValidator,
            AdministrativeComplaintMatcher complaintMatcher) {
        this.registrationValidator = Objects.requireNonNull(registrationValidator, "registrationValidator");
        this.complaintMatcher = Objects.requireNonNull(complaintMatcher, "complaintMatcher");
    }

    public TriageLevel classify(PatientRegistration registration) {
        registrationValidator.validate(registration);

        if (isCritical(registration)) {
            return TriageLevel.CRITICO;
        }

        if (isUrgent(registration)) {
            return TriageLevel.URGENTE;
        }

        if (isUrgentTwo(registration)) {
            return TriageLevel.URGENTE_2;
        }

        return complaintMatcher.matches(registration.quejaPrincipal())
                ? TriageLevel.NO_URGENTE_2
                : TriageLevel.NO_URGENTE;
    }

    private boolean isCritical(PatientRegistration registration) {
        return registration.frecuenciaCardiacaBpm() < CRITICAL_HEART_RATE_MIN
                || registration.frecuenciaCardiacaBpm() > CRITICAL_HEART_RATE_MAX
                || registration.spo2().compareTo(CRITICAL_SPO2_THRESHOLD) < 0
                || registration.presionSistolicaMmhg() < CRITICAL_SYSTOLIC_PRESSURE_MIN
                || registration.gcs() <= CRITICAL_GCS_THRESHOLD;
    }

    private boolean isUrgent(PatientRegistration registration) {
        return between(registration.spo2(), URGENT_SPO2_MIN, URGENT_SPO2_MAX)
                || between(registration.presionSistolicaMmhg(), URGENT_SYSTOLIC_PRESSURE_MIN,
                        URGENT_SYSTOLIC_PRESSURE_MAX)
                || registration.temperaturaC().compareTo(URGENT_TEMPERATURE_THRESHOLD) >= 0
                || registration.gcs() <= URGENT_GCS_THRESHOLD;
    }

    private boolean isUrgentTwo(PatientRegistration registration) {
        return between(registration.frecuenciaCardiacaBpm(), URGENT_2_HEART_RATE_MIN, URGENT_2_HEART_RATE_MAX)
                || betweenExclusive(registration.temperaturaC(), URGENT_2_TEMPERATURE_MIN,
                        URGENT_2_TEMPERATURE_MAX)
                || registration.dolor() >= URGENT_2_PAIN_THRESHOLD;
    }

    private boolean between(Integer value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private boolean between(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private boolean betweenExclusive(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) < 0;
    }
}
