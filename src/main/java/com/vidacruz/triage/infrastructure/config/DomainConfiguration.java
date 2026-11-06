package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.application.port.in.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.port.in.GetPatientUseCase;
import com.vidacruz.triage.application.port.in.RegisterPatientUseCase;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.application.service.ChangePatientStatusService;
import com.vidacruz.triage.application.service.GetPatientService;
import com.vidacruz.triage.application.service.RegisterPatientService;
import com.vidacruz.triage.domain.service.AdministrativeComplaintMatcher;
import com.vidacruz.triage.domain.service.PatientClassifier;
import com.vidacruz.triage.domain.service.PatientRegistrationValidator;
import com.vidacruz.triage.domain.service.PatientStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainConfiguration {

    @Bean
    AdministrativeComplaintMatcher administrativeComplaintMatcher() {
        return new AdministrativeComplaintMatcher();
    }

    @Bean
    PatientRegistrationValidator patientRegistrationValidator() {
        return new PatientRegistrationValidator();
    }

    @Bean
    PatientClassifier patientClassifier(
            PatientRegistrationValidator validator,
            AdministrativeComplaintMatcher matcher) {
        return new PatientClassifier(validator, matcher);
    }

    @Bean
    PatientStateMachine patientStateMachine() {
        return new PatientStateMachine();
    }

    @Bean
    RegisterPatientUseCase registerPatientUseCase(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            PatientClassifier classifier,
            TransactionPort transactions,
            Clock clock) {
        return new RegisterPatientService(patients, audits, outbox, classifier, transactions, clock);
    }

    @Bean
    GetPatientUseCase getPatientUseCase(PatientRepositoryPort patients) {
        return new GetPatientService(patients);
    }

    @Bean
    ChangePatientStatusUseCase changePatientStatusUseCase(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            PatientStateMachine stateMachine,
            TransactionPort transactions,
            Clock clock) {
        return new ChangePatientStatusService(patients, audits, outbox, stateMachine, transactions, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
