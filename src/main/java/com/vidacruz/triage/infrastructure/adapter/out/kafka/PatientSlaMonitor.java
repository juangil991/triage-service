package com.vidacruz.triage.infrastructure.adapter.out.kafka;

import com.vidacruz.triage.application.port.in.EscalateOverduePatientsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PatientSlaMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PatientSlaMonitor.class);

    private final EscalateOverduePatientsUseCase escalation;
    private final AtomicBoolean running = new AtomicBoolean();

    public PatientSlaMonitor(EscalateOverduePatientsUseCase escalation) {
        this.escalation = escalation;
    }

    @Scheduled(fixedDelayString = "${app.triage.escalation.poll-interval-ms:1000}")
    public void monitor() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        escalation.escalateOverdue()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        ignored -> { },
                        error -> logger.error("Falló la evaluación de SLA de pacientes", error));
    }
}
