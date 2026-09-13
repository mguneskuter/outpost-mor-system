DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM merchant_order AS orders
        WHERE (
            SELECT count(*)
            FROM order_payment AS payment
            WHERE payment.order_id = orders.order_id
        ) <> 1
    ) THEN
        RAISE EXCEPTION 'every merchant_order must have exactly one order_payment';
    END IF;
END;
$$;

ALTER TABLE merchant_order
ADD COLUMN payment_reference TEXT,
ADD COLUMN psp_account_id BIGINT REFERENCES account (account_id),
ADD COLUMN psp_reference TEXT,
ADD COLUMN payment_link TEXT,
ADD COLUMN shopper_country_id BIGINT,
ADD COLUMN shopper_country_subdivision_id BIGINT;

UPDATE merchant_order AS orders
SET
    payment_reference = payment.payment_reference,
    psp_account_id = payment.psp_account_id,
    psp_reference = payment.psp_reference,
    payment_link = payment.payment_link,
    shopper_country_id = payment.shopper_country_id,
    shopper_country_subdivision_id = payment.shopper_country_subdivision_id
FROM order_payment AS payment
WHERE payment.order_id = orders.order_id;

ALTER TABLE merchant_order
ALTER COLUMN payment_reference SET NOT NULL,
ALTER COLUMN psp_account_id SET NOT NULL,
ALTER COLUMN shopper_country_id SET NOT NULL,
ADD CONSTRAINT uq_merchant_order_payment_reference UNIQUE (payment_reference),
ADD CONSTRAINT fk_merchant_order_shopper_country
FOREIGN KEY (shopper_country_id) REFERENCES country (country_id),
ADD CONSTRAINT fk_merchant_order_shopper_subdivision
FOREIGN KEY (shopper_country_id, shopper_country_subdivision_id)
REFERENCES country_subdivision (country_id, country_subdivision_id),
DROP COLUMN order_phase,
DROP COLUMN phase_claim_token;

CREATE FUNCTION validate_merchant_order_psp_account() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM account
        WHERE account_id = NEW.psp_account_id
          AND account_type_id IN (SELECT account_type_id FROM account_type WHERE code = 'PSP')
    ) THEN
        RAISE EXCEPTION 'merchant order PSP account must have PSP type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_order_psp_account_type
BEFORE INSERT OR UPDATE OF psp_account_id ON merchant_order
FOR EACH ROW EXECUTE FUNCTION validate_merchant_order_psp_account();

DROP TABLE order_payment;
DROP SEQUENCE order_payment_seq;

ALTER TABLE order_item DROP COLUMN sequence;
