CREATE TABLE psp_configuration (
    account_id BIGINT PRIMARY KEY,
    account_type_id BIGINT NOT NULL,
    base_url TEXT NOT NULL,
    api_key TEXT NOT NULL,
    hmac_secret TEXT NOT NULL,
    CONSTRAINT fk_psp_configuration_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_psp_configuration_psp_type CHECK (account_type_id = 4)
);
