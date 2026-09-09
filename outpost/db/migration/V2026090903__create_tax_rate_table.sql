CREATE SEQUENCE tax_rate_seq;

CREATE TABLE tax_rate (
    tax_rate_id BIGINT PRIMARY KEY DEFAULT nextval('tax_rate_seq'),
    country_id BIGINT NOT NULL,
    country_subdivision_id BIGINT,
    rate NUMERIC(6, 4) NOT NULL,
    CONSTRAINT fk_tax_rate_country
    FOREIGN KEY (country_id) REFERENCES country (country_id),
    CONSTRAINT fk_tax_rate_country_subdivision
    FOREIGN KEY (country_id, country_subdivision_id)
    REFERENCES country_subdivision (country_id, country_subdivision_id),
    CONSTRAINT ck_tax_rate_non_negative CHECK (rate >= 0),
    CONSTRAINT uq_tax_rate_jurisdiction
    UNIQUE NULLS NOT DISTINCT (country_id, country_subdivision_id)
);

ALTER SEQUENCE tax_rate_seq OWNED BY tax_rate.tax_rate_id;
