CREATE SEQUENCE merchant_api_key_seq;
CREATE TABLE merchant_api_key (
    merchant_api_key_id BIGINT PRIMARY KEY DEFAULT nextval(
        'merchant_api_key_seq'
    ),
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    api_key_hash TEXT NOT NULL,
    hmac_secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL,
    CONSTRAINT fk_merchant_api_key_account_type FOREIGN KEY (
        account_id, account_type_id
    )
    REFERENCES account (account_id, account_type_id)
);

CREATE FUNCTION validate_merchant_api_key_account_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id IS DISTINCT FROM
        (SELECT account_type_id FROM account_type WHERE code = 'MERCHANT') THEN
        RAISE EXCEPTION 'merchant API key account must have MERCHANT type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_api_key_account_type
BEFORE INSERT OR UPDATE ON merchant_api_key
FOR EACH ROW EXECUTE FUNCTION validate_merchant_api_key_account_type();

CREATE TABLE merchant_psp (
    account_id BIGINT NOT NULL REFERENCES account (account_id),
    psp_account_id BIGINT NOT NULL REFERENCES account (account_id),
    PRIMARY KEY (account_id, psp_account_id)
);
