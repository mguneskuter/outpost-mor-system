-- A row is a refund whose refund lines' net or tax sum differs from its refund
-- detail's net or tax. Read-only.
SELECT
    t.reference AS refund_reference,
    rd.transaction_id AS refund_id,
    rd.net_quantity,
    rd.tax_quantity,
    coalesce(sum(ri.net_amount), 0) AS line_net_amount,
    coalesce(sum(ri.tax_amount), 0) AS line_tax_amount
FROM refund_detail AS rd
INNER JOIN transaction AS t ON rd.transaction_id = t.transaction_id
LEFT JOIN refund_item AS ri ON rd.transaction_id = ri.refund_id
GROUP BY rd.transaction_id, t.reference
HAVING
    rd.net_quantity <> coalesce(sum(ri.net_amount), 0)
    OR rd.tax_quantity <> coalesce(sum(ri.tax_amount), 0)
ORDER BY rd.transaction_id;
