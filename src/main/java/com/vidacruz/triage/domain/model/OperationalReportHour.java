package com.vidacruz.triage.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OperationalReportHour(
        Instant hora,
        long totalPacientesRegistrados,
        long critico,
        long urgente,
        long urgente2,
        long noUrgente,
        long noUrgente2,
        BigDecimal tiempoPromedioEsperaMinutos,
        BigDecimal p95TiempoEsperaMinutos) {
}
