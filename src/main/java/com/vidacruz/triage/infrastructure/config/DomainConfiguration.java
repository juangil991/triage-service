package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.application.port.out.ClinicalAuditPort;
import com.vidacruz.triage.application.port.out.OutboxPort;
import com.vidacruz.triage.application.port.out.PatientPersistencePort;
import com.vidacruz.triage.application.port.out.TransactionPort;
import com.vidacruz.triage.application.usecase.ChangePatientStatusUseCase;
import com.vidacruz.triage.application.usecase.GetPatientUseCase;
import com.vidacruz.triage.application.usecase.RegisterPatientUseCase;
import com.vidacruz.triage.infrastructure.adapter.in.rest.PatientInputAdapter;
import com.vidacruz.triage.domain.service.AdministrativeComplaintMatcher;
import com.vidacruz.triage.domain.service.PatientClassifier;
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
    PatientClassifier patientClassifier(AdministrativeComplaintMatcher matcher) {
        return new PatientClassifier(matcher);
    }

    @Bean
    PatientStateMachine patientStateMachine() {
        return new PatientStateMachine();
    }

    @Bean
    RegisterPatientUseCase registerPatientUseCase(
            PatientPersistencePort patients,
            ClinicalAuditPort audits,
            OutboxPort outbox,
            PatientClassifier classifier,
            TransactionPort transactions,
            Clock clock) {
        return new RegisterPatientUseCase(patients, audits, outbox, classifier, transactions, clock);
    }

    @Bean
    GetPatientUseCase getPatientUseCase(PatientPersistencePort patients) {
        return new GetPatientUseCase(patients);
    }

    @Bean
    ChangePatientStatusUseCase changePatientStatusUseCase(
            PatientPersistencePort patients,
            ClinicalAuditPort audits,
            OutboxPort outbox,
            PatientStateMachine stateMachine,
            TransactionPort transactions,
            Clock clock) {
        return new ChangePatientStatusUseCase(patients, audits, outbox, stateMachine, transactions, clock);
    }

    @Bean
    PatientInputAdapter patientInputAdapter(
            RegisterPatientUseCase registerPatient,
            GetPatientUseCase getPatient,
            ChangePatientStatusUseCase changePatientStatus) {
        return new PatientInputAdapter(registerPatient, getPatient, changePatientStatus);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
