package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriageSlaPropertiesTest {

    @Test
    void mapsConfiguredSlaToEachTriageLevel() {
        TriageSlaProperties properties = new TriageSlaProperties(1, 2, 3, 4, 5);

        assertEquals(1, properties.minutesFor(TriageLevel.CRITICO));
        assertEquals(2, properties.minutesFor(TriageLevel.URGENTE));
        assertEquals(3, properties.minutesFor(TriageLevel.URGENTE_2));
        assertEquals(4, properties.minutesFor(TriageLevel.NO_URGENTE));
        assertEquals(5, properties.minutesFor(TriageLevel.NO_URGENTE_2));
    }

    @Test
    void rejectsNonPositiveSlaValues() {
        assertThrows(IllegalArgumentException.class, () -> new TriageSlaProperties(0, 2, 3, 4, 5));
    }
}
