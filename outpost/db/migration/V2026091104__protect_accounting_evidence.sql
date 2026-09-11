ALTER TABLE transaction
ADD CONSTRAINT uq_transaction_reference UNIQUE (reference);

ALTER TABLE transaction_event
ADD CONSTRAINT uq_transaction_event_type
UNIQUE (transaction_id, transaction_event_type_id);

CREATE TRIGGER transaction_append_only
BEFORE UPDATE OR DELETE ON transaction
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();

CREATE TRIGGER payment_detail_append_only
BEFORE UPDATE OR DELETE ON payment_detail
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();

CREATE TRIGGER transaction_event_append_only
BEFORE UPDATE OR DELETE ON transaction_event
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();

CREATE FUNCTION reject_account_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE'
       OR (to_jsonb(NEW) - 'is_active')
          IS DISTINCT FROM (to_jsonb(OLD) - 'is_active') THEN
        RAISE EXCEPTION 'account is immutable except for is_active';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER account_immutable
BEFORE UPDATE OR DELETE ON account
FOR EACH ROW EXECUTE FUNCTION reject_account_mutation();
