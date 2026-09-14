-- A refund is stored before the PSP is asked, so its PSP reference arrives
-- later.
ALTER TABLE merchant_refund ALTER COLUMN psp_refund_reference DROP NOT NULL;

DROP TRIGGER merchant_refund_append_only ON merchant_refund;
DROP FUNCTION reject_merchant_refund_mutation();

-- psp_refund_reference is stored once the PSP has acknowledged the refund, so
-- it may go from NULL to a value, or be re-written with its identical value.
-- Every other column is fixed once the refund is inserted.
CREATE FUNCTION reject_merchant_refund_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'merchant refund cannot be deleted';
    END IF;
    IF (to_jsonb(NEW) - 'psp_refund_reference')
       IS DISTINCT FROM (to_jsonb(OLD) - 'psp_refund_reference')
       OR (OLD.psp_refund_reference IS NOT NULL
           AND NEW.psp_refund_reference IS DISTINCT FROM OLD.psp_refund_reference) THEN
        RAISE EXCEPTION 'merchant refund is immutable except for storing psp_refund_reference once';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_refund_immutable
BEFORE UPDATE OR DELETE ON merchant_refund
FOR EACH ROW EXECUTE FUNCTION reject_merchant_refund_mutation();

CREATE SEQUENCE refund_item_seq;
CREATE TABLE refund_item (
    refund_item_id BIGINT PRIMARY KEY DEFAULT nextval('refund_item_seq'),
    refund_id BIGINT NOT NULL REFERENCES merchant_refund (refund_id),
    order_item_id BIGINT NOT NULL REFERENCES order_item (order_item_id),
    refund_failed BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_refund_item_refund_order_item UNIQUE (
        refund_id, order_item_id
    )
);
ALTER SEQUENCE refund_item_seq
OWNED BY refund_item.refund_item_id;

-- Every refund stored so far covered its whole order, and the Ledger booked
-- only the first refund of a payment: the earliest refund of each order
-- claims every line of that order, and any later refund of the same order
-- holds the same lines as failed, so it stays readable and claims nothing.
INSERT INTO refund_item (refund_id, order_item_id, refund_failed)
WITH first_refund AS (
    SELECT DISTINCT ON (order_id)
        refund_id,
        order_id
    FROM merchant_refund
    ORDER BY order_id, refund_id
)

SELECT
    refund.refund_id,
    item.order_item_id,
    refund.refund_id <> first_refund.refund_id AS refund_failed
FROM merchant_refund AS refund
INNER JOIN first_refund ON refund.order_id = first_refund.order_id
INNER JOIN order_item AS item ON refund.order_id = item.order_id;

-- An order line has at most one refund that has not failed.
CREATE UNIQUE INDEX uq_refund_item_claimed_order_item
ON refund_item (order_item_id) WHERE NOT refund_failed;

-- refund_failed records that the PSP refused or failed the refund, which
-- releases the line; it is set once and never cleared, and every other column
-- is fixed once the item is inserted.
CREATE FUNCTION reject_refund_item_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'refund item cannot be deleted';
    END IF;
    IF (to_jsonb(NEW) - 'refund_failed') IS DISTINCT FROM (to_jsonb(OLD) - 'refund_failed')
       OR (OLD.refund_failed AND NOT NEW.refund_failed) THEN
        RAISE EXCEPTION 'refund item is immutable except for marking refund_failed once';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER refund_item_immutable
BEFORE UPDATE OR DELETE ON refund_item
FOR EACH ROW EXECUTE FUNCTION reject_refund_item_mutation();
