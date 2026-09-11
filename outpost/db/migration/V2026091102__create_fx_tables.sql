CREATE SEQUENCE fx_rate_seq;

CREATE TABLE fx_rate (
    fx_rate_id BIGINT PRIMARY KEY DEFAULT nextval('fx_rate_seq'),
    base_currency_id BIGINT NOT NULL,
    quote_currency_id BIGINT NOT NULL,
    rate_date DATE NOT NULL,
    rate NUMERIC(20, 10) NOT NULL,
    source VARCHAR(32) NOT NULL,
    CONSTRAINT fk_fx_rate_base_currency FOREIGN KEY (base_currency_id)
    REFERENCES currency (currency_id),
    CONSTRAINT fk_fx_rate_quote_currency FOREIGN KEY (quote_currency_id)
    REFERENCES currency (currency_id),
    CONSTRAINT ck_fx_rate_positive CHECK (rate > 0),
    CONSTRAINT ck_fx_rate_distinct_currencies CHECK (
        base_currency_id <> quote_currency_id
    ),
    CONSTRAINT uq_fx_rate_pair_date UNIQUE (
        base_currency_id, quote_currency_id, rate_date
    )
);

ALTER SEQUENCE fx_rate_seq OWNED BY fx_rate.fx_rate_id;

CREATE SEQUENCE fx_fee_seq;

CREATE TABLE fx_fee (
    fx_fee_id BIGINT PRIMARY KEY DEFAULT nextval('fx_fee_seq'),
    base_currency_id BIGINT NOT NULL,
    quote_currency_id BIGINT NOT NULL,
    fee_rate_bps INTEGER NOT NULL,
    CONSTRAINT fk_fx_fee_base_currency FOREIGN KEY (base_currency_id)
    REFERENCES currency (currency_id),
    CONSTRAINT fk_fx_fee_quote_currency FOREIGN KEY (quote_currency_id)
    REFERENCES currency (currency_id),
    CONSTRAINT ck_fx_fee_non_negative CHECK (fee_rate_bps >= 0),
    CONSTRAINT ck_fx_fee_distinct_currencies CHECK (
        base_currency_id <> quote_currency_id
    ),
    CONSTRAINT uq_fx_fee_pair UNIQUE (base_currency_id, quote_currency_id)
);

ALTER SEQUENCE fx_fee_seq OWNED BY fx_fee.fx_fee_id;
