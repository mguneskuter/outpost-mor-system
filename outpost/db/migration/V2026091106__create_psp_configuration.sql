CREATE TABLE psp_configuration (
    account_id BIGINT PRIMARY KEY,
    account_type_id BIGINT NOT NULL,
    base_url TEXT NOT NULL,
    api_key TEXT NOT NULL,
    hmac_secret TEXT NOT NULL,
    CONSTRAINT fk_psp_configuration_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id)
);

CREATE FUNCTION validate_psp_configuration_account_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id IS DISTINCT FROM
        (SELECT account_type_id FROM account_type WHERE code = 'PSP') THEN
        RAISE EXCEPTION 'PSP configuration account must have PSP type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER psp_configuration_account_type
BEFORE INSERT OR UPDATE ON psp_configuration
FOR EACH ROW EXECUTE FUNCTION validate_psp_configuration_account_type();
