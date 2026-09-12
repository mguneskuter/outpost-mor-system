CREATE SCHEMA IF NOT EXISTS psp_simulator;

SET search_path = psp_simulator;

CREATE SEQUENCE psp_order_seq;

CREATE TABLE psp_order (
    psp_reference BIGINT PRIMARY KEY DEFAULT nextval('psp_order_seq'),
    psp_code TEXT NOT NULL,
    payment_reference TEXT NOT NULL,
    amount BIGINT NOT NULL,
    currency_code TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_psp_order_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_psp_order_status CHECK (
        status IN ('CREATED', 'AUTHORISED', 'REFUSED', 'CAPTURED', 'CANCELLED')
    ),
    CONSTRAINT uq_psp_order_payment_reference UNIQUE (
        psp_code, payment_reference
    )
);

ALTER SEQUENCE psp_order_seq OWNED BY psp_order.psp_reference;

CREATE SEQUENCE psp_refund_seq;

CREATE TABLE psp_refund (
    psp_refund_reference BIGINT PRIMARY KEY DEFAULT nextval('psp_refund_seq'),
    psp_code TEXT NOT NULL,
    psp_reference BIGINT NOT NULL REFERENCES psp_order (psp_reference),
    refund_reference TEXT NOT NULL,
    amount BIGINT NOT NULL,
    currency_code TEXT NOT NULL,
    accepted BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_psp_refund_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT uq_psp_refund_reference UNIQUE (
        psp_code, refund_reference
    )
);

ALTER SEQUENCE psp_refund_seq OWNED BY psp_refund.psp_refund_reference;
