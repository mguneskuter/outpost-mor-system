CREATE TEMP TABLE seed_merchant_psp (
    account_id BIGINT,
    psp_account_id BIGINT,
    PRIMARY KEY (account_id, psp_account_id)
);
INSERT INTO seed_merchant_psp (account_id, psp_account_id)
SELECT
    merchant.account_id AS merchant_account_id,
    psp.account_id AS psp_account_id
FROM account AS merchant
INNER JOIN account_type AS merchant_type
    ON
        merchant.account_type_id = merchant_type.account_type_id
        AND merchant_type.code = 'MERCHANT'
CROSS JOIN account AS psp
INNER JOIN account_type AS psp_type
    ON
        psp.account_type_id = psp_type.account_type_id
        AND psp_type.code = 'PSP';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_merchant_psp expected
        LEFT JOIN merchant_psp actual USING (account_id, psp_account_id)
        WHERE actual.account_id IS NULL
    ) THEN
        RAISE EXCEPTION 'merchant PSP seed found a divergent row';
    END IF;
END
$$;
INSERT INTO merchant_psp (account_id, psp_account_id)
SELECT
    account_id,
    psp_account_id
FROM seed_merchant_psp
ON CONFLICT (account_id, psp_account_id) DO NOTHING;
DROP TABLE seed_merchant_psp;
