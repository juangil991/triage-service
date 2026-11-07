package com.vidacruz.triage.application.port.out;

import com.vidacruz.triage.domain.model.EscalationAlert;
import reactor.core.publisher.Mono;

public interface MedicalTeamAlertPort {

    Mono<Void> send(EscalationAlert alert);
}
