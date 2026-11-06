package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.exception.InvalidPatientTransitionException;
import com.vidacruz.triage.domain.model.PatientStatus;

public class PatientStateMachine {

    public void validate(PatientStatus current, PatientStatus requested) {
        boolean allowed = (current == PatientStatus.REGISTRADO
                    && requested == PatientStatus.EN_CONSULTA)
                || (current == PatientStatus.EN_CONSULTA && requested == PatientStatus.ALTA);
        if (!allowed) {
            throw new InvalidPatientTransitionException(current, requested);
        }
    }
}
