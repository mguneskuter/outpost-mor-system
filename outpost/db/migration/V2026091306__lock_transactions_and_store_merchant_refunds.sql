DROP TABLE accounting_request_queue_line;
DROP TABLE payment_lock;
DROP TABLE accounting_request_queue;
DROP SEQUENCE accounting_request_queue_seq;
DROP TABLE accounting_request_type;
DROP TABLE accounting_request_status_type;
DROP TABLE accounting_request_result_type;
DROP FUNCTION validate_accounting_request_merchant_account_type();
DROP FUNCTION validate_accounting_request_done_status();

DROP TABLE psp_event_queue;
DROP SEQUENCE psp_event_queue_seq;
DROP TABLE psp_event_code;
DROP TABLE psp_event_status;
DROP TABLE psp_event_result;
DROP FUNCTION validate_psp_event_account_types();
DROP FUNCTION validate_psp_event_done_status();

DROP TABLE refund_item;
DROP FUNCTION reject_refund_item_mutation();

ALTER TABLE merchant_order DROP COLUMN payment_reference;

CREATE TABLE transaction_lock (
    original_reference TEXT PRIMARY KEY,
    locked_ts TIMESTAMPTZ NOT NULL,
    lease_until_ts TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_transaction_lock_lease_after_lock CHECK (
        lease_until_ts > locked_ts
    )
);

CREATE SEQUENCE merchant_refund_seq;
CREATE TABLE merchant_refund (
    refund_id BIGINT PRIMARY KEY DEFAULT nextval('merchant_refund_seq'),
    refund_reference TEXT NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES merchant_order (order_id),
    original_reference TEXT NOT NULL,
    merchant_reference TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    psp_refund_reference TEXT NOT NULL,
    created_ts TIMESTAMPTZ NOT NULL
);
ALTER SEQUENCE merchant_refund_seq OWNED BY merchant_refund.refund_id;

CREATE FUNCTION reject_merchant_refund_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'merchant refund is append-only';
END;
$$;

CREATE TRIGGER merchant_refund_append_only
BEFORE UPDATE OR DELETE ON merchant_refund
FOR EACH ROW EXECUTE FUNCTION reject_merchant_refund_mutation();
