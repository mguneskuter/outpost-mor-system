SET search_path = psp_simulator;

ALTER TABLE psp_refund RENAME COLUMN accepted TO succeeded;
