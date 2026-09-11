CREATE TABLE tax_authority_account (
    country_id BIGINT PRIMARY KEY REFERENCES country (country_id),
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    CONSTRAINT chk_tax_authority_account_type CHECK (account_type_id = 5),
    CONSTRAINT fk_tax_authority_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id)
);
