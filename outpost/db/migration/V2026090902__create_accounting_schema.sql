CREATE SEQUENCE account_seq;
CREATE TABLE account (
    account_id BIGINT PRIMARY KEY DEFAULT nextval('account_seq'),
    account_type_id BIGINT NOT NULL REFERENCES account_type (account_type_id),
    parent_account_id BIGINT REFERENCES account (account_id),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_ts TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_account_id_type UNIQUE (account_id, account_type_id)
);

CREATE SEQUENCE merchant_fee_configuration_seq;
CREATE TABLE merchant_fee_configuration (
    merchant_fee_configuration_id BIGINT PRIMARY KEY
    DEFAULT nextval('merchant_fee_configuration_seq'),
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currency (currency_id),
    fee_mode_id BIGINT NOT NULL REFERENCES fee_mode (fee_mode_id),
    fee_rate_bps BIGINT NOT NULL,
    fee_fixed BIGINT,
    CONSTRAINT uq_merchant_fee_account_currency UNIQUE (
        account_id, currency_id
    ),
    CONSTRAINT fk_merchant_fee_account_type
    FOREIGN KEY (account_id, account_type_id)
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT chk_merchant_fee_rate CHECK (fee_rate_bps BETWEEN 0 AND 1000)
);

CREATE FUNCTION validate_merchant_fee_configuration_account_type()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id <> (SELECT account_type_id FROM account_type WHERE code = 'MERCHANT') THEN
        RAISE EXCEPTION 'merchant fee configuration account must have MERCHANT type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_fee_configuration_account_type
BEFORE INSERT OR UPDATE ON merchant_fee_configuration
FOR EACH ROW
EXECUTE FUNCTION validate_merchant_fee_configuration_account_type();

CREATE FUNCTION validate_merchant_fee_configuration_mode() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT (
        (NEW.fee_mode_id = (SELECT fee_mode_id FROM fee_mode WHERE code = 'PERCENTAGE')
            AND NEW.fee_fixed IS NULL)
        OR (NEW.fee_mode_id =
                (SELECT fee_mode_id FROM fee_mode WHERE code = 'PERCENTAGE_PLUS_FIXED')
            AND NEW.fee_fixed IS NOT NULL AND NEW.fee_fixed >= 0)
    ) THEN
        RAISE EXCEPTION 'merchant fee configuration fee mode and fee_fixed are inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER merchant_fee_configuration_mode
BEFORE INSERT OR UPDATE ON merchant_fee_configuration
FOR EACH ROW EXECUTE FUNCTION validate_merchant_fee_configuration_mode();

CREATE TABLE register_type (
    register_type_id BIGINT PRIMARY KEY,
    register_type_code TEXT NOT NULL UNIQUE
);

CREATE TABLE transaction_type (
    transaction_type_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE transaction_event_type (
    transaction_event_type_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    requires_journal_entry BOOLEAN NOT NULL
);

CREATE TABLE journal_entry_type (
    journal_entry_type_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE SEQUENCE register_seq;
CREATE TABLE register (
    register_id BIGINT PRIMARY KEY DEFAULT nextval('register_seq'),
    account_id BIGINT NOT NULL REFERENCES account (account_id),
    register_type_id BIGINT NOT NULL REFERENCES register_type (
        register_type_id
    ),
    CONSTRAINT uq_register_account_type UNIQUE (account_id, register_type_id)
);

CREATE SEQUENCE transaction_seq;
CREATE TABLE transaction (
    transaction_id BIGINT PRIMARY KEY DEFAULT nextval('transaction_seq'),
    transaction_type_id BIGINT NOT NULL REFERENCES transaction_type (
        transaction_type_id
    ),
    parent_transaction_id BIGINT REFERENCES transaction (transaction_id),
    account_id BIGINT NOT NULL REFERENCES account (account_id),
    reference TEXT NOT NULL,
    quantity BIGINT NOT NULL,
    currency_id BIGINT NOT NULL REFERENCES currency (currency_id),
    created_ts TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_transaction_id_type UNIQUE (
        transaction_id, transaction_type_id
    )
);

CREATE TABLE payment_detail (
    transaction_id BIGINT PRIMARY KEY,
    transaction_type_id BIGINT NOT NULL,
    shopper_country_id BIGINT NOT NULL REFERENCES country (country_id),
    shopper_country_subdivision_id BIGINT,
    psp_account_id BIGINT NOT NULL REFERENCES account (account_id),
    net_quantity BIGINT NOT NULL,
    tax_quantity BIGINT NOT NULL,
    CONSTRAINT fk_payment_detail_payment
    FOREIGN KEY (transaction_id, transaction_type_id)
    REFERENCES transaction (transaction_id, transaction_type_id),
    CONSTRAINT fk_payment_detail_subdivision_country
    FOREIGN KEY (shopper_country_id, shopper_country_subdivision_id)
    REFERENCES country_subdivision (country_id, country_subdivision_id)
);

CREATE FUNCTION validate_payment_detail_payment_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.transaction_type_id <> (SELECT transaction_type_id FROM transaction_type WHERE code = 'PAYMENT') THEN
        RAISE EXCEPTION 'payment detail transaction must have PAYMENT type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER payment_detail_payment_type
BEFORE INSERT OR UPDATE ON payment_detail
FOR EACH ROW EXECUTE FUNCTION validate_payment_detail_payment_type();

CREATE FUNCTION validate_payment_detail_psp_account() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM account
        WHERE account_id = NEW.psp_account_id
          AND account_type_id IN (SELECT account_type_id FROM account_type WHERE code = 'PSP')
    ) THEN
        RAISE EXCEPTION 'payment detail PSP account must have PSP type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER payment_detail_psp_account_type
BEFORE INSERT OR UPDATE ON payment_detail
FOR EACH ROW EXECUTE FUNCTION validate_payment_detail_psp_account();

CREATE FUNCTION protect_referenced_psp_account_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id NOT IN (SELECT account_type_id FROM account_type WHERE code = 'PSP')
       AND EXISTS (SELECT 1 FROM payment_detail WHERE psp_account_id = OLD.account_id) THEN
        RAISE EXCEPTION 'a referenced PSP account cannot change account type';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER referenced_psp_account_type
BEFORE UPDATE OF account_type_id ON account
FOR EACH ROW EXECUTE FUNCTION protect_referenced_psp_account_type();

CREATE SEQUENCE transaction_event_seq;
CREATE TABLE transaction_event (
    transaction_event_id BIGINT PRIMARY KEY DEFAULT nextval(
        'transaction_event_seq'
    ),
    transaction_id BIGINT NOT NULL REFERENCES transaction (transaction_id),
    transaction_event_type_id BIGINT NOT NULL
    REFERENCES transaction_event_type (transaction_event_type_id),
    event_ts TIMESTAMPTZ NOT NULL
);

CREATE SEQUENCE journal_entry_seq;
CREATE TABLE journal_entry (
    journal_entry_id BIGINT PRIMARY KEY DEFAULT nextval('journal_entry_seq'),
    transaction_event_id BIGINT NOT NULL UNIQUE REFERENCES transaction_event (
        transaction_event_id
    ),
    journal_entry_type_id BIGINT NOT NULL REFERENCES journal_entry_type (
        journal_entry_type_id
    ),
    booked TIMESTAMPTZ NOT NULL,
    posted TIMESTAMPTZ NOT NULL
);

CREATE SEQUENCE journal_entry_line_seq;
CREATE TABLE journal_entry_line (
    journal_entry_line_id BIGINT PRIMARY KEY DEFAULT nextval(
        'journal_entry_line_seq'
    ),
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entry (
        journal_entry_id
    ),
    register_id BIGINT NOT NULL REFERENCES register (register_id),
    currency_id BIGINT NOT NULL REFERENCES currency (currency_id),
    quantity BIGINT NOT NULL
);

CREATE FUNCTION reject_accounting_history_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'accounting history is append-only';
END;
$$;

CREATE TRIGGER journal_entry_append_only
BEFORE UPDATE OR DELETE ON journal_entry
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();

CREATE TRIGGER journal_entry_line_append_only
BEFORE UPDATE OR DELETE ON journal_entry_line
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();

CREATE FUNCTION validate_journal_entry_balance() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    entry_id BIGINT;
    currency_total NUMERIC;
    line_currency_id BIGINT;
BEGIN
    entry_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.journal_entry_id ELSE NEW.journal_entry_id END;
    FOR line_currency_id, currency_total IN
        SELECT jel.currency_id, SUM(jel.quantity)
          FROM journal_entry_line jel
         WHERE jel.journal_entry_id = entry_id
         GROUP BY jel.currency_id
    LOOP
        IF currency_total <> 0 THEN
            RAISE EXCEPTION 'journal entry % is unbalanced in currency %',
                entry_id, line_currency_id;
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER journal_entry_line_balance
AFTER INSERT OR UPDATE OR DELETE ON journal_entry_line
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_journal_entry_balance();

CREATE FUNCTION validate_required_journal_entry() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    event_id BIGINT;
    requires_entry BOOLEAN;
    entry_count BIGINT;
BEGIN
    event_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.transaction_event_id
                     ELSE NEW.transaction_event_id END;
    SELECT tet.requires_journal_entry
      INTO requires_entry
      FROM transaction_event te
      JOIN transaction_event_type tet
        ON tet.transaction_event_type_id = te.transaction_event_type_id
     WHERE te.transaction_event_id = event_id;
    IF requires_entry IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT COUNT(*) INTO entry_count
      FROM journal_entry
     WHERE transaction_event_id = event_id;
    IF (requires_entry AND entry_count <> 1)
       OR (NOT requires_entry AND entry_count <> 0) THEN
        RAISE EXCEPTION 'event % has invalid journal entry count %', event_id, entry_count;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER transaction_event_entry_coupling
AFTER INSERT OR UPDATE OR DELETE ON transaction_event
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_required_journal_entry();

CREATE CONSTRAINT TRIGGER journal_entry_event_coupling
AFTER INSERT OR UPDATE OR DELETE ON journal_entry
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_required_journal_entry();

CREATE FUNCTION validate_event_type_coupling() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    event_row RECORD;
    entry_count BIGINT;
BEGIN
    FOR event_row IN
        SELECT te.transaction_event_id
          FROM transaction_event te
         WHERE te.transaction_event_type_id = NEW.transaction_event_type_id
    LOOP
        SELECT COUNT(*) INTO entry_count
          FROM journal_entry
         WHERE transaction_event_id = event_row.transaction_event_id;
        IF (NEW.requires_journal_entry AND entry_count <> 1)
           OR (NOT NEW.requires_journal_entry AND entry_count <> 0) THEN
            RAISE EXCEPTION 'event % has invalid journal entry count %',
                event_row.transaction_event_id, entry_count;
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER transaction_event_type_entry_coupling
AFTER UPDATE OF requires_journal_entry ON transaction_event_type
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_event_type_coupling();
