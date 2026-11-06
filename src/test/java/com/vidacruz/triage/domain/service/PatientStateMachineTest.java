package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatientStateMachineTest {

    private final PatientStateMachine stateMachine = new PatientStateMachine();

    @Test
    void acceptsAllSpecifiedTransitions() {
        assertDoesNotThrow(() -> stateMachine.validate(PatientStatus.REGISTRADO, PatientStatus.EN_CONSULTA));
        assertDoesNotThrow(() -> stateMachine.validate(PatientStatus.EN_CONSULTA, PatientStatus.ALTA));
    }

    @Test
    void rejectsEveryTransitionNotExplicitlyAllowed() {
        Set<String> allowed = Set.of(
                "REGISTRADO:EN_CONSULTA",
                "EN_CONSULTA:ALTA");

        for (PatientStatus current : PatientStatus.values()) {
            for (PatientStatus requested : PatientStatus.values()) {
                if (!allowed.contains(current + ":" + requested)) {
                    assertThrows(InvalidPatientTransitionException.class,
                            () -> stateMachine.validate(current, requested),
                            current + " -> " + requested);
                }
            }
        }
    }
}
