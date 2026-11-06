package com.vidacruz.triage.infrastructure.config;

import com.vidacruz.triage.application.port.out.TransactionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class TransactionConfiguration {

    @Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }

    @Bean
    TransactionPort transactionPort(TransactionalOperator transactionalOperator) {
        return transactionalOperator::transactional;
    }
}
