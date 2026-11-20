
CREATE TABLE integration.outbox_event (
                                          id UUID PRIMARY KEY,

                                          aggregate_type VARCHAR(100) NOT NULL,
                                          aggregate_id UUID NOT NULL,
                                          event_type VARCHAR(100) NOT NULL,

                                          payload JSONB NOT NULL,

                                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                          attempts INTEGER NOT NULL DEFAULT 0,
                                          max_attempts INTEGER NOT NULL DEFAULT 10,

                                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                          locked_until TIMESTAMPTZ,
                                          processed_at TIMESTAMPTZ,

                                          last_error TEXT,

                                          CONSTRAINT chk_outbox_event_status
                                              CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD')),

                                          CONSTRAINT chk_outbox_event_attempts
                                              CHECK (attempts >= 0 AND max_attempts > 0),

                                          CONSTRAINT chk_outbox_event_processing_lock
                                              CHECK (
                                                  (status = 'PROCESSING' AND locked_until IS NOT NULL)
                                                      OR
                                                  (status <> 'PROCESSING' AND locked_until IS NULL)
                                                  )
);

CREATE INDEX idx_outbox_event_claim
    ON integration.outbox_event (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_event_expired_lock
    ON integration.outbox_event (locked_until)
    WHERE status = 'PROCESSING';
