CREATE TABLE psp_event_code (
    psp_event_code_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE psp_event_status (
    psp_event_status_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE psp_event_result (
    psp_event_result_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE SEQUENCE psp_event_queue_seq;
CREATE TABLE psp_event_queue (
    queue_id BIGINT PRIMARY KEY DEFAULT nextval('psp_event_queue_seq'),
    created_ts TIMESTAMPTZ NOT NULL,
    done_ts TIMESTAMPTZ,
    done BOOLEAN NOT NULL DEFAULT false,
    status_id BIGINT NOT NULL REFERENCES psp_event_status (psp_event_status_id),
    result_id BIGINT REFERENCES psp_event_result (psp_event_result_id),
    type_id BIGINT NOT NULL REFERENCES psp_event_code (psp_event_code_id),
    reference TEXT NOT NULL,
    original_reference TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    psp_account_id BIGINT NOT NULL,
    psp_account_type_id BIGINT NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT fk_psp_event_merchant_account FOREIGN KEY (
        account_id, account_type_id
    )
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_psp_event_merchant_type CHECK (account_type_id = 2),
    CONSTRAINT fk_psp_event_psp_account FOREIGN KEY (
        psp_account_id, psp_account_type_id
    )
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_psp_event_psp_type CHECK (psp_account_type_id = 4),
    CONSTRAINT uq_psp_event UNIQUE (psp_account_id, reference, type_id),
    CONSTRAINT chk_psp_event_done_status CHECK (
        done = (status_id = 3)
    ),
    CONSTRAINT chk_psp_event_done_fields CHECK (
        (done_ts IS NOT null) = done AND (result_id IS NOT null) = done
    )
);

CREATE INDEX ix_psp_event_queue_original_reference -- noqa: PG01
ON psp_event_queue (original_reference, queue_id)
WHERE NOT done;
