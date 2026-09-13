CREATE FUNCTION reject_order_item_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'order item is append-only';
END;
$$;

CREATE TRIGGER order_item_append_only
BEFORE UPDATE OR DELETE ON order_item
FOR EACH ROW EXECUTE FUNCTION reject_order_item_mutation();

CREATE FUNCTION reject_refund_item_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'refund item is append-only';
END;
$$;

CREATE TRIGGER refund_item_append_only
BEFORE UPDATE OR DELETE ON refund_item
FOR EACH ROW EXECUTE FUNCTION reject_refund_item_mutation();

-- psp_reference and payment_link are stored after the PSP call, so each may go
-- from NULL to a value, or be re-written with its identical value. Every other
-- column, including any added later, is fixed once the order is inserted.
CREATE FUNCTION reject_merchant_order_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'merchant order cannot be deleted';
    END IF;
    IF (to_jsonb(NEW) - 'psp_reference' - 'payment_link')
       IS DISTINCT FROM (to_jsonb(OLD) - 'psp_reference' - 'payment_link')
       OR (OLD.psp_reference IS NOT NULL
           AND NEW.psp_reference IS DISTINCT FROM OLD.psp_reference)
       OR (OLD.payment_link IS NOT NULL
           AND NEW.payment_link IS DISTINCT FROM OLD.payment_link) THEN
        RAISE EXCEPTION 'merchant order is immutable except for storing psp_reference and payment_link once';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_order_immutable
BEFORE UPDATE OR DELETE ON merchant_order
FOR EACH ROW EXECUTE FUNCTION reject_merchant_order_mutation();
