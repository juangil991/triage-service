package com.vidacruz.triage.application.port.out;

import com.vidacruz.triage.domain.model.OperationalReportHour;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

public interface OperationalReportRepositoryPort {

    Flux<OperationalReportHour> findByRegistrationRange(OffsetDateTime desde, OffsetDateTime hasta);
}
