DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM account_type_register_type
        WHERE register_type_id >= 100
    ) THEN
        RAISE EXCEPTION 'register type IDs used by account mappings must be below 100';
    END IF;
END
$$;

CREATE TEMP TABLE seed_register (
    register_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    register_type_id BIGINT NOT NULL
);

INSERT INTO seed_register (register_id, account_id, register_type_id)
SELECT
    account.account_id * 100 + mapping.register_type_id AS register_id,
    account.account_id,
    mapping.register_type_id
FROM account
INNER JOIN account_type_register_type AS mapping
    ON account.account_type_id = mapping.account_type_id
WHERE (
    (account.account_id, account.code) IN (
        (100, 'OUTPOST'),
        (200, 'DEMO_MERCHANT'),
        (201, 'DEMO_MERCHANT_PAYOUT'),
        (210, 'DEMO_MERCHANT_2'),
        (211, 'DEMO_MERCHANT_2_PAYOUT'),
        (300, 'DEMO_PSP')
    )
    OR EXISTS (
        SELECT 1
        FROM country
        WHERE
            account.account_id = 1000 + country.country_id
            AND account.code = 'TAX_AUTHORITY_' || country.iso_code
    )
)
AND account.account_id <> 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_register expected
        JOIN register actual ON actual.register_id = expected.register_id
        WHERE actual.account_id <> expected.account_id
           OR actual.register_type_id <> expected.register_type_id
    ) THEN
        RAISE EXCEPTION 'register seed found a divergent register';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM seed_register expected
        JOIN register actual
          ON actual.account_id = expected.account_id
         AND actual.register_type_id = expected.register_type_id
        WHERE actual.register_id <> expected.register_id
    ) THEN
        RAISE EXCEPTION 'register seed found a register with a divergent ID';
    END IF;
END
$$;

INSERT INTO register (register_id, account_id, register_type_id)
SELECT
    register_id,
    account_id,
    register_type_id
FROM seed_register
ON CONFLICT (register_id) DO NOTHING;

SELECT setval(
    'register_seq',
    greatest(
        coalesce((SELECT max(register_id) FROM register), 1),
        (SELECT last_value FROM register_seq)
    ),
    TRUE
);

DROP TABLE seed_register;
