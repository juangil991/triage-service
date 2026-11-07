package com.vidacruz.triage.infrastructure.adapter.out.webhook;

import com.vidacruz.triage.application.port.out.MedicalTeamAlertPort;
import com.vidacruz.triage.domain.model.EscalationAlert;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

public class WebhookMedicalTeamAlertAdapter implements MedicalTeamAlertPort {

    private final WebClient webClient;
    private final String webhookUrl;

    public WebhookMedicalTeamAlertAdapter(WebClient webClient, String webhookUrl) {
        Assert.hasText(webhookUrl, "La URL del webhook de alertas debe estar configurada");
        URI endpoint = URI.create(webhookUrl);
        Assert.isTrue(
                ("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                        && endpoint.getHost() != null,
                "La URL del webhook de alertas debe ser una URL HTTP(S) válida");
        this.webClient = webClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public Mono<Void> send(EscalationAlert alert) {
        return webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", alert.eventId().toString())
                .bodyValue(alert)
                .retrieve()
                .toBodilessEntity()
                .then()
                .timeout(Duration.ofSeconds(10));
    }
}
