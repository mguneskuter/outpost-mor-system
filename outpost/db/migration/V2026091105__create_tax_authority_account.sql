CREATE TABLE tax_authority_account (
    country_id BIGINT PRIMARY KEY REFERENCES country (country_id),
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    CONSTRAINT fk_tax_authority_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id)
);

CREATE FUNCTION validate_tax_authority_account_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id IS DISTINCT FROM
        (SELECT account_type_id FROM account_type WHERE code = 'TAX_AUTHORITY') THEN
        RAISE EXCEPTION 'tax authority account must have TAX_AUTHORITY type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tax_authority_account_type
BEFORE INSERT OR UPDATE ON tax_authority_account
FOR EACH ROW EXECUTE FUNCTION validate_tax_authority_account_type();
