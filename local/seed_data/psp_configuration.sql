CREATE TEMP TABLE seed_psp_configuration (
    account_id BIGINT PRIMARY KEY,
    account_type_id BIGINT NOT NULL,
    base_url TEXT NOT NULL,
    api_key TEXT NOT NULL,
    hmac_secret TEXT NOT NULL
);

INSERT INTO seed_psp_configuration
(account_id, account_type_id, base_url, api_key, hmac_secret)
SELECT
    account.account_id,
    account.account_type_id,
    :'psp_simulator_base_url' AS base_url, -- noqa: LT01
    :'psp_simulator_api_key' AS api_key, -- noqa: LT01
    :'psp_simulator_hmac_secret' AS hmac_secret -- noqa: LT01
FROM account
WHERE account.account_type_id = (
    SELECT account_type.account_type_id
    FROM account_type
    WHERE account_type.code = 'PSP'
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_psp_configuration expected
        JOIN psp_configuration actual USING (account_id)
        WHERE actual.account_type_id IS DISTINCT FROM expected.account_type_id
           OR actual.base_url IS DISTINCT FROM expected.base_url
           OR actual.api_key IS DISTINCT FROM expected.api_key
           OR actual.hmac_secret IS DISTINCT FROM expected.hmac_secret
    ) THEN
        RAISE EXCEPTION 'psp configuration seed found a divergent row';
    END IF;
END
$$;

INSERT INTO psp_configuration
(account_id, account_type_id, base_url, api_key, hmac_secret)
SELECT
    seed_psp_configuration.account_id,
    seed_psp_configuration.account_type_id,
    seed_psp_configuration.base_url,
    seed_psp_configuration.api_key,
    seed_psp_configuration.hmac_secret
FROM seed_psp_configuration
ON CONFLICT (account_id) DO NOTHING;

DROP TABLE seed_psp_configuration;
