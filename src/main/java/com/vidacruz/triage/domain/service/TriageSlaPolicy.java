package com.vidacruz.triage.domain.service;

import com.vidacruz.triage.domain.model.TriageLevel;

@FunctionalInterface
public interface TriageSlaPolicy {

    int minutesFor(TriageLevel level);
}
