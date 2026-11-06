package com.vidacruz.triage.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdministrativeComplaintMatcherTest {

    private final AdministrativeComplaintMatcher matcher = new AdministrativeComplaintMatcher();

    @Test
    void recognizesAllProvidedAdministrativeReferenceDescriptions() {
        List<String> descriptions = List.of(
                "The patient reports difficulties scheduling a medical appointment through the hospital's booking system.",
                "The patient complains about excessive waiting times for administrative registration.",
                "The patient reports delays in verifying their health insurance coverage.",
                "The patient disputes an incorrect charge on their medical bill.",
                "The patient reports that their medical records are missing from the hospital's system.",
                "The patient requests correction of inaccurate personal information in their medical profile.",
                "The patient complains about delays in obtaining authorization for a medical procedure.",
                "The patient reports unprofessional or unhelpful treatment by administrative staff.",
                "The patient did not receive confirmation of their scheduled appointment.",
                "The patient reports administrative delays in authorizing prescribed medication.",
                "The patient complains about delays in receiving medical certificates or administrative documents.",
                "The patient reports receiving duplicate invoices for the same medical service.",
                "The patient reports that the appointment date displayed in the system differs from the confirmed date.",
                "The patient requests an update to their insurance provider information.",
                "The patient complains that a previously submitted administrative complaint has not received a response.");

        descriptions.forEach(description -> assertTrue(matcher.matches(description), description));
    }

    @Test
    void matchesSpanishKeywordsWithoutAccentSensitivity() {
        assertTrue(matcher.matches("Problema con la confirmacion de la cita"));
    }
}
