CREATE INDEX ix_journal_entry_line_journal_entry_id -- noqa: PG01
ON journal_entry_line (journal_entry_id);

CREATE INDEX ix_transaction_parent_transaction_id -- noqa: PG01
ON transaction (parent_transaction_id);
