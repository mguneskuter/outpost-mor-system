-- A row is a PAYMENT or REFUND transaction whose detail is missing, carries a
-- negative net or tax, or does not add up to the transaction's gross quantity.
SELECT
    tt.code AS transaction_type,
    t.reference AS transaction_reference,
    t.transaction_id,
    t.quantity AS gross_quantity,
    COALESCE(pd.net_quantity, rd.net_quantity) AS net_quantity,
    COALESCE(pd.tax_quantity, rd.tax_quantity) AS tax_quantity,
    CASE
        WHEN COALESCE(pd.transaction_id, rd.transaction_id) IS NULL
            THEN 'MISSING_DETAIL'
        WHEN
            COALESCE(pd.net_quantity, rd.net_quantity) < 0
            OR COALESCE(pd.tax_quantity, rd.tax_quantity) < 0
            THEN 'NEGATIVE_AMOUNT'
        ELSE 'GROSS_NOT_NET_PLUS_TAX'
    END AS mismatch
FROM transaction AS t
INNER JOIN transaction_type AS tt
    ON t.transaction_type_id = tt.transaction_type_id
LEFT JOIN payment_detail AS pd
    ON tt.code = 'PAYMENT' AND t.transaction_id = pd.transaction_id
LEFT JOIN refund_detail AS rd
    ON tt.code = 'REFUND' AND t.transaction_id = rd.transaction_id
WHERE
    tt.code IN ('PAYMENT', 'REFUND')
    AND (
        COALESCE(pd.transaction_id, rd.transaction_id) IS NULL
        OR COALESCE(pd.net_quantity, rd.net_quantity) < 0
        OR COALESCE(pd.tax_quantity, rd.tax_quantity) < 0
        OR t.quantity
        <> COALESCE(pd.net_quantity, rd.net_quantity)
        + COALESCE(pd.tax_quantity, rd.tax_quantity)
    )
ORDER BY t.transaction_id;
