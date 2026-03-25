CREATE TABLE tasks
(
    id            VARCHAR(13)  NOT NULL,
    image_url     TEXT         NOT NULL,
    fingerprint   VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    job_id        VARCHAR(255),
    result        TEXT,
    fail_reason   TEXT,
    retry_count   INT          NOT NULL DEFAULT 0,
    poll_count    INT          NOT NULL DEFAULT 0,
    next_poll_at  TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT uq_tasks_fingerprint UNIQUE (fingerprint)
);

CREATE INDEX idx_tasks_status_next_poll_at ON tasks (status, next_poll_at)
    WHERE status IN ('SUBMITTED', 'PROCESSING', 'RETRY_WAITING', 'PENDING');
