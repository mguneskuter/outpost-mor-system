ALTER TABLE merchant_order
ADD COLUMN request_fingerprint TEXT NOT NULL DEFAULT '',
ADD COLUMN order_phase TEXT NOT NULL DEFAULT 'ORDER_PERSISTED',
ADD COLUMN phase_claim_token UUID,
ADD CONSTRAINT chk_merchant_order_phase CHECK (
    order_phase IN (
        'ORDER_PERSISTED',
        'LEDGER_CREATED',
        'PSP_CREATED',
        'COMPLETED'
    )
);

ALTER TABLE order_payment
ADD COLUMN payment_link TEXT,
ADD COLUMN shopper_country_id BIGINT,
ADD COLUMN shopper_country_subdivision_id BIGINT;

UPDATE order_payment payment
SET
    shopper_country_id = shopper.country_id,
    shopper_country_subdivision_id = shopper.country_subdivision_id
FROM merchant_order AS orders
INNER JOIN shopper_detail AS shopper ON orders.shopper_id = shopper.shopper_id
WHERE payment.order_id = orders.order_id;

ALTER TABLE order_payment
ALTER COLUMN shopper_country_id SET NOT NULL,
ADD CONSTRAINT fk_order_payment_shopper_country
FOREIGN KEY (shopper_country_id) REFERENCES country (country_id),
ADD CONSTRAINT fk_order_payment_shopper_subdivision
FOREIGN KEY (shopper_country_id, shopper_country_subdivision_id)
REFERENCES country_subdivision (country_id, country_subdivision_id);
