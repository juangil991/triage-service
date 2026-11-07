package com.vidacruz.triage.application.service;

import com.vidacruz.triage.application.port.in.GetOperationalReportUseCase;
import com.vidacruz.triage.application.port.out.OperationalReportRepositoryPort;
import com.vidacruz.triage.domain.model.OperationalReportHour;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

public class GetOperationalReportService implements GetOperationalReportUseCase {

    private final OperationalReportRepositoryPort reports;

    public GetOperationalReportService(OperationalReportRepositoryPort reports) {
        this.reports = reports;
    }

    @Override
    public Flux<OperationalReportHour> getReport(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            return Flux.error(new IllegalArgumentException("desde debe ser anterior a hasta"));
        }
        return reports.findByRegistrationRange(desde, hasta);
    }
}
