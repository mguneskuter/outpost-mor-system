-- Signed journal entry line totals per account, register, and currency;
-- positive is a debit.
SELECT
    a.code AS account_code,
    at.code AS account_type,
    rt.register_type_code AS register_type,
    c.currency_code,
    SUM(jel.quantity) AS total_quantity
FROM journal_entry_line AS jel
INNER JOIN register AS r ON jel.register_id = r.register_id
INNER JOIN account AS a ON r.account_id = a.account_id
INNER JOIN account_type AS at ON a.account_type_id = at.account_type_id
INNER JOIN register_type AS rt ON r.register_type_id = rt.register_type_id
INNER JOIN currency AS c ON jel.currency_id = c.currency_id
GROUP BY a.code, at.code, rt.register_type_code, c.currency_code
ORDER BY a.code, rt.register_type_code, c.currency_code;
