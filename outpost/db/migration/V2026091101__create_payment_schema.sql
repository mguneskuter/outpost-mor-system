CREATE SEQUENCE shopper_detail_seq;
CREATE TABLE shopper_detail (
    shopper_id BIGINT PRIMARY KEY DEFAULT nextval('shopper_detail_seq'),
    email TEXT NOT NULL,
    full_name TEXT NOT NULL,
    country_id BIGINT NOT NULL,
    country_subdivision_id BIGINT,
    postal_code TEXT,
    CONSTRAINT fk_shopper_detail_country_subdivision
    FOREIGN KEY (country_id, country_subdivision_id)
    REFERENCES country_subdivision (country_id, country_subdivision_id),
    CONSTRAINT uq_shopper_detail_email UNIQUE (email)
);

CREATE SEQUENCE merchant_order_seq;
CREATE TABLE merchant_order (
    order_id BIGINT PRIMARY KEY DEFAULT nextval('merchant_order_seq'),
    order_reference TEXT NOT NULL UNIQUE,
    merchant_reference TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    shopper_id BIGINT NOT NULL REFERENCES shopper_detail (shopper_id),
    currency_id BIGINT NOT NULL REFERENCES currency (currency_id),
    net_amount BIGINT NOT NULL,
    tax_amount BIGINT NOT NULL,
    gross_amount BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_ts TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_merchant_order_account_idempotency UNIQUE (
        account_id, idempotency_key
    ),
    CONSTRAINT fk_merchant_order_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_merchant_order_merchant_type CHECK (account_type_id = 2),
    CONSTRAINT chk_merchant_order_amounts_non_negative CHECK (
        net_amount >= 0 AND tax_amount >= 0 AND gross_amount >= 0
    ),
    CONSTRAINT chk_merchant_order_gross_equals_net_plus_tax CHECK (
        gross_amount = net_amount + tax_amount
    )
);

CREATE SEQUENCE order_item_seq;
CREATE TABLE order_item (
    order_item_id BIGINT PRIMARY KEY DEFAULT nextval('order_item_seq'),
    order_id BIGINT NOT NULL REFERENCES merchant_order (order_id),
    sequence INT NOT NULL,
    product_type_id BIGINT NOT NULL REFERENCES product_type (product_type_id),
    order_line_reference TEXT NOT NULL UNIQUE,
    merchant_line_reference TEXT NOT NULL,
    net_amount BIGINT NOT NULL,
    tax_amount BIGINT NOT NULL,
    tax_rate NUMERIC(6, 4) NOT NULL,
    CONSTRAINT uq_order_item_order_merchant_line UNIQUE (
        order_id, merchant_line_reference
    ),
    CONSTRAINT chk_order_item_amounts_non_negative CHECK (
        net_amount >= 0 AND tax_amount >= 0
    ),
    CONSTRAINT chk_order_item_tax_rate_non_negative CHECK (tax_rate >= 0)
);

CREATE SEQUENCE order_payment_seq;
CREATE TABLE order_payment (
    order_payment_id BIGINT PRIMARY KEY DEFAULT nextval(
        'order_payment_seq'
    ),
    order_id BIGINT NOT NULL REFERENCES merchant_order (order_id),
    payment_reference TEXT NOT NULL UNIQUE,
    psp_account_id BIGINT NOT NULL,
    psp_account_type_id BIGINT NOT NULL,
    psp_reference TEXT,
    created_ts TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_payment_psp_account_type
    FOREIGN KEY (psp_account_id, psp_account_type_id)
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_order_payment_psp_type CHECK (psp_account_type_id = 4)
);

CREATE TABLE refund_item (
    refund_id BIGINT NOT NULL REFERENCES transaction (transaction_id),
    order_item_id BIGINT NOT NULL REFERENCES order_item (order_item_id),
    net_amount BIGINT NOT NULL,
    tax_amount BIGINT NOT NULL,
    PRIMARY KEY (refund_id, order_item_id),
    CONSTRAINT chk_refund_item_amounts_non_negative CHECK (
        net_amount >= 0 AND tax_amount >= 0
    )
);
