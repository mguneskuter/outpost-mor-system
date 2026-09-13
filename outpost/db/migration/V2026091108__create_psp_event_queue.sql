CREATE TABLE psp_event_code (
    psp_event_code_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE psp_event_status (
    psp_event_status_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE TABLE psp_event_result (
    psp_event_result_id BIGINT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE
);

CREATE SEQUENCE psp_event_queue_seq;
CREATE TABLE psp_event_queue (
    queue_id BIGINT PRIMARY KEY DEFAULT nextval('psp_event_queue_seq'),
    created_ts TIMESTAMPTZ NOT NULL,
    done_ts TIMESTAMPTZ,
    done BOOLEAN NOT NULL DEFAULT false,
    status_id BIGINT NOT NULL REFERENCES psp_event_status (psp_event_status_id),
    result_id BIGINT REFERENCES psp_event_result (psp_event_result_id),
    type_id BIGINT NOT NULL REFERENCES psp_event_code (psp_event_code_id),
    reference TEXT NOT NULL,
    original_reference TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    account_type_id BIGINT NOT NULL,
    psp_account_id BIGINT NOT NULL,
    psp_account_type_id BIGINT NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT fk_psp_event_merchant_account FOREIGN KEY (
        account_id, account_type_id
    )
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT fk_psp_event_psp_account FOREIGN KEY (
        psp_account_id, psp_account_type_id
    )
    REFERENCES account (account_id, account_type_id),
    CONSTRAINT uq_psp_event UNIQUE (psp_account_id, reference, type_id),
    CONSTRAINT chk_psp_event_done_fields CHECK (
        (done_ts IS NOT null) = done AND (result_id IS NOT null) = done
    )
);

CREATE FUNCTION validate_psp_event_account_types() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.account_type_id IS DISTINCT FROM
        (SELECT account_type_id FROM account_type WHERE code = 'MERCHANT')
        OR NEW.psp_account_type_id IS DISTINCT FROM
        (SELECT account_type_id FROM account_type WHERE code = 'PSP') THEN
        RAISE EXCEPTION 'PSP event accounts must have MERCHANT and PSP types';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER psp_event_queue_account_types
BEFORE INSERT OR UPDATE ON psp_event_queue
FOR EACH ROW EXECUTE FUNCTION validate_psp_event_account_types();

CREATE FUNCTION validate_psp_event_done_status() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.done IS DISTINCT FROM (NEW.status_id IS NOT DISTINCT FROM
        (SELECT psp_event_status_id FROM psp_event_status WHERE code = 'DONE')) THEN
        RAISE EXCEPTION 'PSP event done flag must match DONE status';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER psp_event_queue_done_status
BEFORE INSERT OR UPDATE ON psp_event_queue
FOR EACH ROW EXECUTE FUNCTION validate_psp_event_done_status();

CREATE INDEX ix_psp_event_queue_original_reference -- noqa: PG01
ON psp_event_queue (original_reference, queue_id)
WHERE NOT done;
