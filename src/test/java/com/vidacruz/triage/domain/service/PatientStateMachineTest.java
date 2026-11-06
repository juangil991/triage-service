package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.model.PatientStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatientStateMachineTest {

    private final PatientStateMachine stateMachine = new PatientStateMachine();

    @Test
    void acceptsAllSpecifiedTransitions() {
        assertDoesNotThrow(() -> stateMachine.validate(PatientStatus.REGISTRADO, PatientStatus.EN_CONSULTA));
        assertDoesNotThrow(() -> stateMachine.validate(PatientStatus.REGISTRADO, PatientStatus.ALTA));
        assertDoesNotThrow(() -> stateMachine.validate(PatientStatus.EN_CONSULTA, PatientStatus.ALTA));
    }

    @Test
    void rejectsTransitionsFromDischargedAndRepeatedState() {
        assertThrows(InvalidPatientTransitionException.class,
                () -> stateMachine.validate(PatientStatus.ALTA, PatientStatus.EN_CONSULTA));
        assertThrows(InvalidPatientTransitionException.class,
                () -> stateMachine.validate(PatientStatus.REGISTRADO, PatientStatus.REGISTRADO));
    }
}
