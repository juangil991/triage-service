package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientException;
import com.vidacruz.triage.domain.model.PatientRegistration;
import com.vidacruz.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatientClassifierTest {

    private PatientClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PatientClassifier(new PatientRegistrationValidator(), new AdministrativeComplaintMatcher());
    }

    @Test
    void requiresCollaborators() {
        assertThrows(NullPointerException.class,
                () -> new PatientClassifier(null, new AdministrativeComplaintMatcher()));
        assertThrows(NullPointerException.class,
                () -> new PatientClassifier(new PatientRegistrationValidator(), null));
    }

    @Test
    void criticalCriteriaTakePriorityOverLowerLevels() {
        PatientRegistration patient = patient(130, 120, 80, "98", "37.0", 8, 15, "solicitud administrativa");

        assertEquals(TriageLevel.CRITICO, classifier.classify(patient));
    }

    @Test
    void urgentCriteriaTakePriorityOverUrgentTwo() {
        PatientRegistration patient = patient(100, 100, 70, "95", "38.5", 8, 13, "dolor leve");

        assertEquals(TriageLevel.URGENTE, classifier.classify(patient));
    }

    @Test
    void classifiesAtUrgentTwoPainBoundary() {
        PatientRegistration patient = patient(80, 120, 80, "98", "37.0", 7, 15, "dolor");

        assertEquals(TriageLevel.URGENTE_2, classifier.classify(patient));
    }

    @Test
    void classifiesLowerUrgentTwoTemperatureBoundaryAsUrgentTwo() {
        PatientRegistration patient = patient(80, 120, 80, "98", "38.0", 2, 15, "fiebre");

        assertEquals(TriageLevel.URGENTE_2, classifier.classify(patient));
    }

    @Test
    void usesAdministrativeKeywordsOnlyAfterUrgentLevelsAreExcluded() {
        PatientRegistration administrative = patient(80, 120, 80, "98", "37.0", 2, 15,
                "Tengo un problema con la confirmación de mi cita");
        PatientRegistration ordinary = patient(80, 120, 80, "98", "37.0", 2, 15,
                "consulta general");
        PatientRegistration urgentAdministrative = patient(80, 120, 80, "98", "38.5", 2, 15,
                "problema con una cita");

        assertEquals(TriageLevel.NO_URGENTE_2, classifier.classify(administrative));
        assertEquals(TriageLevel.NO_URGENTE, classifier.classify(ordinary));
        assertEquals(TriageLevel.URGENTE, classifier.classify(urgentAdministrative));
    }

    @Test
    void classifiesVerificationMatrixE1ThroughE6() {
        List<MatrixCase> cases = List.of(
                new MatrixCase("E1", patient(130, 180, 110, "97", "36.8", 8, 15, "Dolor torácico"),
                        TriageLevel.CRITICO),
                new MatrixCase("E2", patient(95, 150, 95, "91", "37.0", 4, 15, "Dificultad para respirar"),
                        TriageLevel.URGENTE),
                new MatrixCase("E3", patient(88, 130, 85, "98", "38.2", 6, 15, "Fiebre"),
                        TriageLevel.URGENTE_2),
                new MatrixCase("E4", patient(72, 120, 80, "99", "36.5", 2, 15, "Herida por cortar"),
                        TriageLevel.NO_URGENTE),
                new MatrixCase("E5", patient(70, 115, 75, "99", "36.4", 0, 15, "Renovación de recetario"),
                        TriageLevel.NO_URGENTE_2),
                new MatrixCase("E6", patient(35, 80, 50, "85", "35.9", 0, 6, "Inconsciente"),
                        TriageLevel.CRITICO));

        for (MatrixCase testCase : cases) {
            assertEquals(testCase.expected(), classifier.classify(testCase.registration()), testCase.id());
        }
    }

    @Test
    void rejectsDiastolicPressureNotLowerThanSystolic() {
        PatientRegistration patient = patient(80, 120, 120, "98", "37.0", 2, 15, "consulta");

        InvalidPatientException exception = assertThrows(
                InvalidPatientException.class, () -> classifier.classify(patient));

        assertEquals("presionDiastolicaMmhg", exception.field());
    }

    @Test
    void rejectsOutOfRangeValuesWithTheInvalidField() {
        List<InvalidCase> cases = List.of(
                new InvalidCase(patient("AB", 40, 80, 120, 80, "98", "37", 2, 15, "consulta"), "nombreCompleto"),
                new InvalidCase(patient("Paciente Prueba", -1, 80, 120, 80, "98", "37", 2, 15, "consulta"), "edad"),
                new InvalidCase(patient("Paciente Prueba", 121, 80, 120, 80, "98", "37", 2, 15, "consulta"), "edad"),
                new InvalidCase(patient("Paciente Prueba", 40, 19, 120, 80, "98", "37", 2, 15, "consulta"),
                        "frecuenciaCardiacaBpm"),
                new InvalidCase(patient("Paciente Prueba", 40, 251, 120, 80, "98", "37", 2, 15, "consulta"),
                        "frecuenciaCardiacaBpm"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 59, 30, "98", "37", 2, 15, "consulta"),
                        "presionSistolicaMmhg"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 261, 30, "98", "37", 2, 15, "consulta"),
                        "presionSistolicaMmhg"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 29, "98", "37", 2, 15, "consulta"),
                        "presionDiastolicaMmhg"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 161, "98", "37", 2, 15, "consulta"),
                        "presionDiastolicaMmhg"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "69.9", "37", 2, 15, "consulta"),
                        "spo2"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "100.1", "37", 2, 15, "consulta"),
                        "spo2"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "33.9", 2, 15, "consulta"),
                        "temperaturaC"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "42.1", 2, 15, "consulta"),
                        "temperaturaC"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "37", -1, 15, "consulta"),
                        "dolor"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "37", 11, 15, "consulta"),
                        "dolor"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "37", 2, 16, "consulta"), "gcs"),
                new InvalidCase(patient("Paciente Prueba", 40, 80, 120, 80, "98", "37", 2, 2, "consulta"), "gcs"));

        for (InvalidCase invalidCase : cases) {
            InvalidPatientException exception = assertThrows(
                    InvalidPatientException.class, () -> classifier.classify(invalidCase.registration()));
            assertEquals(invalidCase.field(), exception.field());
        }
    }

    @Test
    void acceptsInclusiveVitalSignBoundaries() {
        PatientRegistration patient = patient(40, 100, 60, "70", "34.0", 0, 3, "consulta");

        assertEquals(TriageLevel.CRITICO, classifier.classify(patient));
    }

    @Test
    void acceptsLowerAndUpperBoundariesForAllValidatedFields() {
        PatientRegistration lower = patient("Ana", 0, 20, 60, 30, "70", "34.0", 0, 3, "x");
        PatientRegistration upper = patient("A".repeat(100), 120, 250, 260, 160, "100", "42.0", 10, 15, "x");

        assertDoesNotThrow(() -> classifier.classify(lower));
        assertDoesNotThrow(() -> classifier.classify(upper));
    }

    @Test
    void validatesRequiredTextAndUnicodeNameLength() {
        assertEquals("body", assertThrows(InvalidPatientException.class,
                () -> classifier.classify(null)).field());
        assertEquals("nombreCompleto", assertThrows(InvalidPatientException.class,
                () -> classifier.classify(patient(" \t ", 40, 80, 120, 80, "98", "37", 2, 15, "consulta"))).field());
        assertEquals("quejaPrincipal", assertThrows(InvalidPatientException.class,
                () -> classifier.classify(patient("Ana", 40, 80, 120, 80, "98", "37", 2, 15, " \t "))).field());

        assertDoesNotThrow(() -> classifier.classify(
                patient("😀😀😀", 40, 80, 120, 80, "98", "37", 2, 15, "consulta")));
        assertEquals("nombreCompleto", assertThrows(InvalidPatientException.class,
                () -> classifier.classify(patient("😀".repeat(101), 40, 80, 120, 80, "98", "37", 2, 15,
                        "consulta"))).field());
    }

    @Test
    void classifiesAtAndImmediatelyAroundClinicalThresholds() {
        List<ClassificationCase> cases = List.of(
                new ClassificationCase(patient(80, 120, 80, "89.9", "37", 2, 15, "consulta"), TriageLevel.CRITICO),
                new ClassificationCase(patient(80, 120, 80, "90", "37", 2, 15, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 120, 80, "93", "37", 2, 15, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 120, 80, "93.1", "37", 2, 15, "consulta"),
                        TriageLevel.NO_URGENTE),
                new ClassificationCase(patient(80, 120, 80, "98", "38.49", 2, 15, "consulta"),
                        TriageLevel.URGENTE_2),
                new ClassificationCase(patient(80, 120, 80, "98", "38.5", 2, 15, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 120, 80, "98", "37", 2, 8, "consulta"), TriageLevel.CRITICO),
                new ClassificationCase(patient(80, 120, 80, "98", "37", 2, 9, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 120, 80, "98", "37", 2, 14, "consulta"),
                        TriageLevel.NO_URGENTE),
                new ClassificationCase(patient(39, 120, 80, "98", "37", 2, 15, "consulta"), TriageLevel.CRITICO),
                new ClassificationCase(patient(40, 120, 80, "98", "37", 2, 15, "consulta"),
                        TriageLevel.NO_URGENTE),
                new ClassificationCase(patient(120, 120, 80, "98", "37", 2, 15, "consulta"),
                        TriageLevel.URGENTE_2),
                new ClassificationCase(patient(121, 120, 80, "98", "37", 2, 15, "consulta"), TriageLevel.CRITICO),
                new ClassificationCase(patient(80, 89, 60, "98", "37", 2, 15, "consulta"), TriageLevel.CRITICO),
                new ClassificationCase(patient(80, 90, 60, "98", "37", 2, 15, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 100, 60, "98", "37", 2, 15, "consulta"), TriageLevel.URGENTE),
                new ClassificationCase(patient(80, 101, 60, "98", "37", 2, 15, "consulta"),
                        TriageLevel.NO_URGENTE),
                new ClassificationCase(patient(80, 120, 80, "98", "37", 6, 15, "consulta"),
                        TriageLevel.NO_URGENTE),
                new ClassificationCase(patient(80, 120, 80, "98", "37", 7, 15, "consulta"),
                        TriageLevel.URGENTE_2));

        for (ClassificationCase testCase : cases) {
            assertEquals(
                    testCase.expected(),
                    classifier.classify(testCase.registration()),
                    testCase.registration().toString());
        }
    }

    private PatientRegistration patient(
            int heartRate,
            int systolic,
            int diastolic,
            String spo2,
            String temperature,
            int pain,
            int gcs,
            String complaint) {
        return patient("Paciente Prueba", 40, heartRate, systolic, diastolic, spo2, temperature, pain, gcs, complaint);
    }

    private PatientRegistration patient(
            String name,
            int age,
            int heartRate,
            int systolic,
            int diastolic,
            String spo2,
            String temperature,
            int pain,
            int gcs,
            String complaint) {
        return new PatientRegistration(
                name,
                age,
                heartRate,
                systolic,
                diastolic,
                new BigDecimal(spo2),
                new BigDecimal(temperature),
                pain,
                gcs,
                complaint);
    }

    private record InvalidCase(PatientRegistration registration, String field) {
    }

    private record ClassificationCase(PatientRegistration registration, TriageLevel expected) {
    }

    private record MatrixCase(String id, PatientRegistration registration, TriageLevel expected) {
    }
}
