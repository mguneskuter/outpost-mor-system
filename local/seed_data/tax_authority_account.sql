CREATE TEMP TABLE seed_tax_authority_account (
    country_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL
);

INSERT INTO seed_tax_authority_account (country_id, account_id, account_type_id)
SELECT
    country.country_id,
    account.account_id,
    account.account_type_id
FROM country
INNER JOIN account ON account.code = 'TAX_AUTHORITY_' || country.iso_code;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM country
        LEFT JOIN seed_tax_authority_account expected
          ON expected.country_id = country.country_id
        WHERE expected.country_id IS NULL
    ) THEN
        RAISE EXCEPTION 'tax authority account seed is missing a country';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM seed_tax_authority_account expected
        JOIN tax_authority_account actual USING (country_id)
        WHERE actual.account_id <> expected.account_id
           OR actual.account_type_id <> expected.account_type_id
    ) THEN
        RAISE EXCEPTION 'tax authority account seed found a divergent row';
    END IF;
END
$$;

INSERT INTO tax_authority_account (country_id, account_id, account_type_id)
SELECT
    country_id,
    account_id,
    account_type_id
FROM seed_tax_authority_account
ON CONFLICT (country_id) DO NOTHING;

DROP TABLE seed_tax_authority_account;
