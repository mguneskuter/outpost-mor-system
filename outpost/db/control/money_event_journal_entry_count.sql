-- A row is a transaction event whose journal entry count breaks its event
-- type's rule: a money event owns exactly one entry, any other event owns none.
SELECT
    t.reference AS transaction_reference,
    te.transaction_event_id,
    tet.code AS transaction_event_type,
    COUNT(je.journal_entry_id) AS journal_entry_count
FROM transaction_event AS te
INNER JOIN transaction AS t ON te.transaction_id = t.transaction_id
INNER JOIN transaction_event_type AS tet
    ON te.transaction_event_type_id = tet.transaction_event_type_id
LEFT JOIN journal_entry AS je
    ON te.transaction_event_id = je.transaction_event_id
GROUP BY
    t.reference, te.transaction_event_id, tet.code, tet.requires_journal_entry
HAVING
    COUNT(je.journal_entry_id)
    <> CASE WHEN tet.requires_journal_entry THEN 1 ELSE 0 END
ORDER BY te.transaction_event_id;
