SET search_path = psp_simulator;

-- A PSP reference is an opaque text the simulator issues, not a counter.
ALTER TABLE psp_refund
DROP CONSTRAINT psp_refund_psp_reference_fkey;

ALTER TABLE psp_order
ALTER COLUMN psp_reference DROP DEFAULT,
ALTER COLUMN psp_reference TYPE TEXT USING psp_reference::TEXT;

ALTER TABLE psp_refund
ALTER COLUMN psp_refund_reference DROP DEFAULT,
ALTER COLUMN psp_refund_reference TYPE TEXT USING psp_refund_reference::TEXT,
ALTER COLUMN psp_reference TYPE TEXT USING psp_reference::TEXT,
ADD CONSTRAINT psp_refund_psp_reference_fkey
FOREIGN KEY (psp_reference) REFERENCES psp_order (psp_reference);

DROP SEQUENCE psp_order_seq;
DROP SEQUENCE psp_refund_seq;
