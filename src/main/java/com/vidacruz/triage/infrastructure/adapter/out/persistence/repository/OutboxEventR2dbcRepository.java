package com.vidacruz.triage.infrastructure.adapter.out.persistence.repository;

import com.vidacruz.triage.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface OutboxEventR2dbcRepository extends ReactiveCrudRepository<OutboxEventEntity, UUID> {

    @Query("""
            INSERT INTO outbox_event (id, event_type, payload, created_at)
            VALUES (:id, :eventType, CAST(:payload AS jsonb), :createdAt)
            """)
    @Modifying
    Mono<Integer> insertEvent(
            @Param("id") UUID id,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("createdAt") OffsetDateTime createdAt);

    @Query("""
            SELECT id, event_type, payload::text AS payload, created_at, published_at,
                   next_attempt_at, send_attempts, last_error
            FROM outbox_event
            WHERE published_at IS NULL
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :limit
            """)
    Flux<OutboxEventEntity> findPending(@Param("limit") int limit);

    @Query("UPDATE outbox_event SET published_at = CURRENT_TIMESTAMP WHERE id = :id")
    @Modifying
    Mono<Integer> markPublished(@Param("id") UUID id);

    @Query("""
            UPDATE outbox_event
            SET send_attempts = send_attempts + 1,
                last_error = :error,
                next_attempt_at = CURRENT_TIMESTAMP
                    + LEAST(
                        INTERVAL '5 minutes',
                        INTERVAL '1 second' * POWER(2::double precision, LEAST(send_attempts, 9)::double precision)
                    )
            WHERE id = :id
            """)
    @Modifying
    Mono<Integer> recordFailure(@Param("id") UUID id, @Param("error") String error);
}
