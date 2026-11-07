package com.vidacruz.triage.application.port.in;

import com.vidacruz.triage.domain.model.OperationalReportHour;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

public interface GetOperationalReportUseCase {

    Flux<OperationalReportHour> getReport(OffsetDateTime desde, OffsetDateTime hasta);
}
