package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.application.port.in.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.port.in.EscalateOverduePatientsUseCase;
import com.vidacruz.triage.application.port.in.GetOperationalReportUseCase;
import com.vidacruz.triage.application.port.in.GetPatientUseCase;
import com.vidacruz.triage.application.port.in.RegisterPatientUseCase;
import com.vidacruz.triage.application.port.out.ClinicalAuditRepositoryPort;
import com.vidacruz.triage.application.port.out.OutboxRepositoryPort;
import com.vidacruz.triage.application.port.out.OperationalReportRepositoryPort;
import com.vidacruz.triage.application.port.out.PatientRepositoryPort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.application.service.ChangePatientStatusService;
import com.vidacruz.triage.application.service.EscalateOverduePatientsService;
import com.vidacruz.triage.application.service.GetOperationalReportService;
import com.vidacruz.triage.application.service.GetPatientService;
import com.vidacruz.triage.application.service.RegisterPatientService;
import com.vidacruz.triage.domain.service.AdministrativeComplaintMatcher;
import com.vidacruz.triage.domain.service.PatientClassifier;
import com.vidacruz.triage.domain.service.PatientRegistrationValidator;
import com.vidacruz.triage.domain.service.PatientStateMachine;
import com.vidacruz.triage.domain.service.TriageSlaPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TriageSlaProperties.class)
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
    TriageSlaPolicy triageSlaPolicy(TriageSlaProperties properties) {
        return properties::minutesFor;
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
            Clock clock,
            TriageSlaPolicy slaPolicy) {
        return new RegisterPatientService(patients, audits, outbox, classifier, transactions, clock, slaPolicy);
    }

    @Bean
    GetPatientUseCase getPatientUseCase(PatientRepositoryPort patients) {
        return new GetPatientService(patients);
    }

    @Bean
    GetOperationalReportUseCase getOperationalReportUseCase(OperationalReportRepositoryPort reports) {
        return new GetOperationalReportService(reports);
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
    EscalateOverduePatientsUseCase escalateOverduePatientsUseCase(
            PatientRepositoryPort patients,
            ClinicalAuditRepositoryPort audits,
            OutboxRepositoryPort outbox,
            TransactionPort transactions,
            Clock clock) {
        return new EscalateOverduePatientsService(patients, audits, outbox, transactions, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
