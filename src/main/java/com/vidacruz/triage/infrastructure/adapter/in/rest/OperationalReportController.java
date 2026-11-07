package com.vidacruz.triage.infrastructure.adapter.in.rest;

import com.vidacruz.triage.application.port.in.GetOperationalReportUseCase;
import com.vidacruz.triage.domain.model.OperationalReportHour;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/triage/reportes")
public class OperationalReportController {

    private final GetOperationalReportUseCase getOperationalReport;

    public OperationalReportController(GetOperationalReportUseCase getOperationalReport) {
        this.getOperationalReport = getOperationalReport;
    }

    @GetMapping("/operacion")
    Flux<OperationalReportHour> getOperationalReport(
            @RequestParam OffsetDateTime desde,
            @RequestParam OffsetDateTime hasta) {
        return getOperationalReport.getReport(desde, hasta);
    }
}
