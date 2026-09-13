-- A row is a currency whose journal entry lines, summed across every account
-- and register, are not zero.
SELECT
    c.currency_code,
    SUM(jel.quantity) AS total_quantity
FROM journal_entry_line AS jel
INNER JOIN currency AS c ON jel.currency_id = c.currency_id
GROUP BY c.currency_code
HAVING SUM(jel.quantity) <> 0
ORDER BY c.currency_code;
