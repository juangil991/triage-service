package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.domain.model.TriageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.triage.sla")
public record TriageSlaProperties(
        int critico,
        int urgente,
        int urgente2,
        int noUrgente,
        int noUrgente2) {

    public TriageSlaProperties {
        if (critico <= 0 || urgente <= 0 || urgente2 <= 0 || noUrgente <= 0 || noUrgente2 <= 0) {
            throw new IllegalArgumentException("Todos los SLA deben ser mayores que cero");
        }
    }

    public int minutesFor(TriageLevel level) {
        return switch (level) {
            case CRITICO -> critico;
            case URGENTE -> urgente;
            case URGENTE_2 -> urgente2;
            case NO_URGENTE -> noUrgente;
            case NO_URGENTE_2 -> noUrgente2;
        };
    }
}
