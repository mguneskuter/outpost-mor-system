CREATE TABLE refund_detail (
    transaction_id BIGINT PRIMARY KEY,
    transaction_type_id BIGINT NOT NULL,
    net_quantity BIGINT NOT NULL,
    tax_quantity BIGINT NOT NULL,
    CONSTRAINT fk_refund_detail_refund
    FOREIGN KEY (transaction_id, transaction_type_id)
    REFERENCES transaction (transaction_id, transaction_type_id),
    CONSTRAINT chk_refund_detail_refund_type CHECK (transaction_type_id = 3),
    CONSTRAINT chk_refund_detail_amounts_non_negative CHECK (
        net_quantity >= 0 AND tax_quantity >= 0
    )
);

CREATE TRIGGER refund_detail_append_only
BEFORE UPDATE OR DELETE ON refund_detail
FOR EACH ROW EXECUTE FUNCTION reject_accounting_history_mutation();
