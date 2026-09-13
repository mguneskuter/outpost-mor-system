-- A row is an order whose stored net or tax differs from the sum of its order
-- lines' net or tax. Read-only.
SELECT
    o.order_reference,
    o.order_id,
    o.net_amount,
    o.tax_amount,
    coalesce(sum(i.net_amount), 0) AS line_net_amount,
    coalesce(sum(i.tax_amount), 0) AS line_tax_amount
FROM merchant_order AS o
LEFT JOIN order_item AS i ON o.order_id = i.order_id
GROUP BY o.order_id
HAVING
    o.net_amount <> coalesce(sum(i.net_amount), 0)
    OR o.tax_amount <> coalesce(sum(i.tax_amount), 0)
ORDER BY o.order_id;
