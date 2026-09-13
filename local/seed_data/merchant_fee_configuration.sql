CREATE TEMP TABLE seed_merchant_fee_configuration (
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    fee_mode_id BIGINT NOT NULL,
    fee_rate_bps BIGINT NOT NULL,
    fee_fixed BIGINT,
    PRIMARY KEY (account_id, currency_id)
);

INSERT INTO seed_merchant_fee_configuration
(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps, fee_fixed)
SELECT
    account.account_id,
    account.account_type_id,
    currency.currency_id,
    fee_mode.fee_mode_id,
    500 AS fee_rate_bps,
    NULL AS fee_fixed
FROM account
INNER JOIN
    account_type
    ON account.account_type_id = account_type.account_type_id
INNER JOIN currency ON currency.currency_code IN ('EUR', 'USD')
INNER JOIN fee_mode ON fee_mode.code = 'PERCENTAGE'
WHERE account_type.code = 'MERCHANT';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM seed_merchant_fee_configuration expected
        JOIN merchant_fee_configuration actual
          ON actual.account_id = expected.account_id
         AND actual.currency_id = expected.currency_id
        WHERE actual.account_type_id IS DISTINCT FROM expected.account_type_id
           OR actual.fee_mode_id IS DISTINCT FROM expected.fee_mode_id
           OR actual.fee_rate_bps IS DISTINCT FROM expected.fee_rate_bps
           OR actual.fee_fixed IS DISTINCT FROM expected.fee_fixed
    ) THEN
        RAISE EXCEPTION 'merchant fee configuration seed found a divergent row';
    END IF;
END
$$;

INSERT INTO merchant_fee_configuration
(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps, fee_fixed)
SELECT
    account_id,
    account_type_id,
    currency_id,
    fee_mode_id,
    fee_rate_bps,
    fee_fixed
FROM seed_merchant_fee_configuration
ON CONFLICT (account_id, currency_id) DO NOTHING;

DROP TABLE seed_merchant_fee_configuration;
