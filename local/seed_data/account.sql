CREATE TEMP TABLE seed_account (
    account_id BIGINT PRIMARY KEY,
    account_type_code TEXT NOT NULL,
    parent_account_id BIGINT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_ts TIMESTAMPTZ NOT NULL
);

-- 1 is the root; 100–199 platform; 200–299 demo merchant;
-- 300–399 demo PSP; 1001–1999 tax authorities.
INSERT INTO seed_account (
    account_id, account_type_code, parent_account_id, code, name, created_ts
)
VALUES
(1, 'ROOT', NULL, 'ROOT', 'Outpost Chart Root', '2026-01-01 00:00:00+00'),
(100, 'PLATFORM', 1, 'OUTPOST', 'Outpost', '2026-01-01 00:00:00+00'),
(
    200, 'MERCHANT', 1, 'DEMO_MERCHANT', 'Demo Merchant',
    '2026-01-01 00:00:00+00'
),
(
    201, 'BANK_ACCOUNT', 200, 'DEMO_MERCHANT_PAYOUT',
    'Demo Merchant Payout Account', '2026-01-01 00:00:00+00'
),
(
    210, 'MERCHANT', 1, 'DEMO_MERCHANT_2', 'Demo Merchant 2',
    '2026-01-01 00:00:00+00'
),
(
    211, 'BANK_ACCOUNT', 210, 'DEMO_MERCHANT_2_PAYOUT',
    'Demo Merchant 2 Payout Account', '2026-01-01 00:00:00+00'
),
(300, 'PSP', 1, 'DEMO_PSP', 'Demo PSP', '2026-01-01 00:00:00+00'),
(310, 'PSP', 1, 'DEMO_PSP_2', 'Demo PSP 2', '2026-01-01 00:00:00+00');

INSERT INTO seed_account (
    account_id, account_type_code, parent_account_id, code, name, created_ts
)
SELECT
    1000 + country.country_id AS account_id,
    'TAX_AUTHORITY' AS account_type_code,
    1 AS parent_account_id,
    'TAX_AUTHORITY_' || country.iso_code AS code,
    country.name || ' Tax Authority' AS name,
    '2026-01-01 00:00:00+00' AS created_ts
FROM country;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_account expected
        LEFT JOIN account_type
          ON account_type.code = expected.account_type_code
        WHERE account_type.account_type_id IS NULL
    ) THEN
        RAISE EXCEPTION 'account seed refers to an unknown account type';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM seed_account expected
        JOIN account actual ON actual.account_id = expected.account_id
        JOIN account_type actual_type
          ON actual_type.account_type_id = actual.account_type_id
        WHERE actual_type.code IS DISTINCT FROM expected.account_type_code
           OR actual.parent_account_id IS DISTINCT FROM expected.parent_account_id
           OR actual.code IS DISTINCT FROM expected.code
           OR actual.name IS DISTINCT FROM expected.name
           OR actual.is_active IS DISTINCT FROM TRUE
           OR actual.created_ts IS DISTINCT FROM expected.created_ts
    ) THEN
        RAISE EXCEPTION 'account seed found a divergent account';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM seed_account expected
        JOIN account actual ON actual.code = expected.code
        WHERE actual.account_id <> expected.account_id
    ) THEN
        RAISE EXCEPTION 'account seed found a code owned by another account';
    END IF;
END
$$;

INSERT INTO account (
    account_id,
    account_type_id,
    parent_account_id,
    code,
    name,
    is_active,
    created_ts
)
SELECT
    expected.account_id,
    account_type.account_type_id,
    expected.parent_account_id,
    expected.code,
    expected.name,
    TRUE AS is_active,
    expected.created_ts
FROM seed_account AS expected
INNER JOIN account_type
    ON expected.account_type_code = account_type.code
ON CONFLICT (account_id) DO NOTHING;

SELECT setval(
    'account_seq',
    greatest(
        coalesce((SELECT max(account_id) FROM account), 1),
        (SELECT last_value FROM account_seq)
    ),
    TRUE
);

DROP TABLE seed_account;
