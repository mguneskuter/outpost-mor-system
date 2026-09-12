CREATE TABLE accounting_request_type (
    accounting_request_type_id BIGINT PRIMARY KEY,
    accounting_request_type_code TEXT NOT NULL UNIQUE
);

CREATE TABLE accounting_request_status (
    accounting_request_status_id BIGINT PRIMARY KEY,
    accounting_request_status_code TEXT NOT NULL UNIQUE
);

CREATE TABLE accounting_request_result (
    accounting_request_result_id BIGINT PRIMARY KEY,
    accounting_request_result_code TEXT NOT NULL UNIQUE
);

CREATE SEQUENCE accounting_request_queue_seq;
CREATE TABLE accounting_request_queue (
    queue_id BIGINT PRIMARY KEY DEFAULT nextval('accounting_request_queue_seq'),
    created_ts TIMESTAMPTZ NOT NULL,
    done_ts TIMESTAMPTZ,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    status_id BIGINT NOT NULL REFERENCES accounting_request_status (
        accounting_request_status_id
    ),
    result_id BIGINT REFERENCES accounting_request_result (
        accounting_request_result_id
    ),
    type_id BIGINT NOT NULL REFERENCES accounting_request_type (
        accounting_request_type_id
    ),
    reference TEXT NOT NULL,
    original_reference TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    psp_event_queue_id BIGINT UNIQUE REFERENCES psp_event_queue (queue_id),
    idempotency_key TEXT,
    merchant_reference TEXT,
    success BOOLEAN,
    amount BIGINT,
    currency_id BIGINT REFERENCES currency (currency_id),
    psp_reference TEXT,
    CONSTRAINT uq_accounting_request_type_reference UNIQUE (type_id, reference),
    CONSTRAINT uq_accounting_request_account_idempotency UNIQUE (
        account_id, idempotency_key
    ),
    CONSTRAINT fk_accounting_request_merchant_account FOREIGN KEY (
        account_id, account_type_id
    ) REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_accounting_request_merchant_type CHECK (account_type_id = 2),
    CONSTRAINT chk_accounting_request_done_status CHECK (
        done = (status_id = 3)
    ),
    CONSTRAINT chk_accounting_request_done_columns CHECK (
        (done_ts IS NOT NULL) = done AND (result_id IS NOT NULL) = done
    ),
    CONSTRAINT chk_accounting_request_amount_non_negative CHECK (
        amount IS NULL OR amount >= 0
    )
);

CREATE INDEX ix_accounting_request_queue_original_reference -- noqa: PG01
ON accounting_request_queue (original_reference, queue_id)
WHERE NOT done;

CREATE TABLE accounting_request_queue_line (
    queue_id BIGINT NOT NULL REFERENCES accounting_request_queue (queue_id),
    order_line_reference TEXT NOT NULL,
    amount BIGINT,
    PRIMARY KEY (queue_id, order_line_reference),
    CONSTRAINT chk_accounting_request_line_amount_non_negative
    CHECK (amount IS NULL OR amount >= 0)
);

CREATE TABLE payment_lock (
    transaction_id BIGINT PRIMARY KEY REFERENCES transaction (transaction_id),
    queue_id BIGINT NOT NULL REFERENCES accounting_request_queue (queue_id),
    locked_ts TIMESTAMPTZ NOT NULL,
    lease_until_ts TIMESTAMPTZ NOT NULL
);
