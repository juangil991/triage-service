package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.application.port.out.MedicalTeamAlertPort;
import com.vidacruz.triage.infrastructure.adapter.out.webhook.WebhookMedicalTeamAlertAdapter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.alerts.webhook.enabled", havingValue = "true")
public class EscalationAlertConfiguration {

    @Bean
    KafkaReceiver<String, String> escalationAlertKafkaReceiver(
            @Value("${app.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topic}") String topic,
            @Value("${app.alerts.webhook.consumer-group-id}") String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(properties)
                .subscription(List.of(topic));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    MedicalTeamAlertPort medicalTeamAlertPort(
            WebClient.Builder webClientBuilder,
            @Value("${app.alerts.webhook.url}") String webhookUrl) {
        return new WebhookMedicalTeamAlertAdapter(webClientBuilder.build(), webhookUrl);
    }
}
