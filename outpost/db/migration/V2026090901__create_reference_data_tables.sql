CREATE TABLE country (
    country_id BIGINT PRIMARY KEY,
    iso_code VARCHAR(2) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_country_iso_code UNIQUE (iso_code)
);

CREATE TABLE country_subdivision (
    country_subdivision_id BIGINT PRIMARY KEY,
    country_id BIGINT NOT NULL,
    code VARCHAR(8) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_country_subdivision_country
    FOREIGN KEY (country_id) REFERENCES country (country_id),
    CONSTRAINT uq_country_subdivision_country_code UNIQUE (country_id, code),
    CONSTRAINT uq_country_subdivision_country_id UNIQUE (
        country_id, country_subdivision_id
    )
);

CREATE TABLE currency (
    currency_id BIGINT PRIMARY KEY,
    currency_code VARCHAR(3) NOT NULL,
    exponent INT NOT NULL,
    CONSTRAINT uq_currency_code UNIQUE (currency_code)
);

CREATE TABLE product_type (
    product_type_id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_product_type_code UNIQUE (code)
);

CREATE TABLE account_type (
    account_type_id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_account_type_code UNIQUE (code)
);

CREATE TABLE fee_mode (
    fee_mode_id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    CONSTRAINT uq_fee_mode_code UNIQUE (code)
);
