package br.com.ateliedigital.outbox.repository;

import br.com.ateliedigital.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;



public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM integration.outbox_event
            WHERE (
                status = 'PENDING'
                AND next_attempt_at <= :now
            )
            OR (
                status = 'PROCESSING'
                AND locked_until <= :now
            )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findClaimable(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
