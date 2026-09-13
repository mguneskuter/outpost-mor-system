-- A row is a journal entry that disagrees with the template its source event
-- requires.
--
-- The templates are restated here rather than read from the Java that builds
-- entries, so the control checks stored history independently of that code.
-- Positive quantity is a debit.
--
-- mismatch:
--   SOURCE       the entry type or source transaction type does not belong to
--                the event type
--   CARDINALITY  the entry has a different number of lines than its template
--   ROLE         a template line is missing, a line has no template line, or a
--                line books to another account than the role requires
--   SIGN         a line's sign is opposite to its role's direction
--   CURRENCY     a line's currency is not the payment's currency
--   FORMULA      a line's quantity differs from the amount its role derives
--                from stored evidence
--
-- The fee is the merchant PENDING_FEE line of the payment's FEE_PENDING entry.
-- A REFUND books to the tax authority used by the payment's CAPTURE entry; a
-- CAPTURE books to the tax authority mapped for the payment's shopper country.
WITH template_line (
    transaction_event_type, account_type, register_type, direction, amount
) AS (
    VALUES
    ('ORDER_CREATED', 'MERCHANT', 'PENDING_FEE', 1, 'FEE'),
    ('ORDER_CREATED', 'PLATFORM', 'PENDING_FEE', -1, 'FEE'),
    ('REFUSED', 'MERCHANT', 'PENDING_FEE', -1, 'FEE'),
    ('REFUSED', 'PLATFORM', 'PENDING_FEE', 1, 'FEE'),
    ('CANCELLED', 'MERCHANT', 'PENDING_FEE', -1, 'FEE'),
    ('CANCELLED', 'PLATFORM', 'PENDING_FEE', 1, 'FEE'),
    ('CAPTURE_FAILED', 'MERCHANT', 'PENDING_FEE', -1, 'FEE'),
    ('CAPTURE_FAILED', 'PLATFORM', 'PENDING_FEE', 1, 'FEE'),
    ('CAPTURED', 'PSP', 'PSP_RECEIVABLE', 1, 'GROSS'),
    ('CAPTURED', 'TAX_AUTHORITY', 'TAX_PAYABLE', -1, 'TAX'),
    ('CAPTURED', 'MERCHANT', 'MERCHANT_PAYABLE', -1, 'MERCHANT_PROCEEDS'),
    ('CAPTURED', 'PLATFORM', 'FEE_REVENUE', -1, 'FEE'),
    ('CAPTURED', 'MERCHANT', 'PENDING_FEE', -1, 'FEE'),
    ('CAPTURED', 'PLATFORM', 'PENDING_FEE', 1, 'FEE'),
    ('REFUNDED', 'PSP', 'PSP_RECEIVABLE', -1, 'GROSS'),
    ('REFUNDED', 'TAX_AUTHORITY', 'TAX_PAYABLE', 1, 'TAX'),
    ('REFUNDED', 'MERCHANT', 'MERCHANT_PAYABLE', 1, 'NET')
),

template_source (
    transaction_event_type, transaction_type, journal_entry_type
) AS (
    VALUES
    ('ORDER_CREATED', 'PAYMENT', 'FEE_PENDING'),
    ('REFUSED', 'PAYMENT', 'FEE_RELEASE'),
    ('CANCELLED', 'PAYMENT', 'FEE_RELEASE'),
    ('CAPTURE_FAILED', 'CAPTURE', 'FEE_RELEASE'),
    ('CAPTURED', 'CAPTURE', 'CAPTURE'),
    ('REFUNDED', 'REFUND', 'REFUND')
),

entry AS (
    SELECT
        je.journal_entry_id,
        jet.code AS journal_entry_type,
        te.transaction_event_id,
        tet.code AS transaction_event_type,
        source_transaction.transaction_id AS source_transaction_id,
        source_transaction.reference AS transaction_reference,
        source_type.code AS transaction_type,
        source_transaction.quantity AS source_quantity,
        payment.transaction_id AS payment_id,
        payment.account_id AS merchant_account_id,
        payment.currency_id AS payment_currency_id,
        payment.quantity AS payment_quantity
    FROM journal_entry AS je
    INNER JOIN journal_entry_type AS jet
        ON je.journal_entry_type_id = jet.journal_entry_type_id
    INNER JOIN transaction_event AS te
        ON je.transaction_event_id = te.transaction_event_id
    INNER JOIN transaction_event_type AS tet
        ON te.transaction_event_type_id = tet.transaction_event_type_id
    INNER JOIN transaction AS source_transaction
        ON te.transaction_id = source_transaction.transaction_id
    INNER JOIN transaction_type AS source_type
        ON
            source_transaction.transaction_type_id
            = source_type.transaction_type_id
    INNER JOIN transaction AS payment
        ON
            COALESCE(
                source_transaction.parent_transaction_id,
                source_transaction.transaction_id
            )
            = payment.transaction_id
),

line AS (
    SELECT
        jel.journal_entry_line_id,
        jel.journal_entry_id,
        jel.quantity,
        jel.currency_id,
        r.account_id,
        at.code AS account_type,
        rt.register_type_code AS register_type
    FROM journal_entry_line AS jel
    INNER JOIN register AS r ON jel.register_id = r.register_id
    INNER JOIN register_type AS rt ON r.register_type_id = rt.register_type_id
    INNER JOIN account AS a ON r.account_id = a.account_id
    INNER JOIN account_type AS at ON a.account_type_id = at.account_type_id
),

payment_fee AS (
    SELECT
        entry.payment_id,
        SUM(line.quantity) AS fee
    FROM entry
    INNER JOIN line ON entry.journal_entry_id = line.journal_entry_id
    WHERE
        entry.transaction_event_type = 'ORDER_CREATED'
        AND line.register_type = 'PENDING_FEE'
        AND line.account_id = entry.merchant_account_id
    GROUP BY entry.payment_id
),

capture_tax_authority AS (
    SELECT
        entry.payment_id,
        MIN(line.account_id) AS account_id
    FROM entry
    INNER JOIN line ON entry.journal_entry_id = line.journal_entry_id
    WHERE
        entry.transaction_event_type = 'CAPTURED'
        AND line.register_type = 'TAX_PAYABLE'
    GROUP BY entry.payment_id
),

-- The platform role is the singleton account the Ledger books every platform
-- line to.
platform_account AS (
    SELECT a.account_id
    FROM account AS a
    INNER JOIN account_type AS at ON a.account_type_id = at.account_type_id
    WHERE a.code = 'OUTPOST' AND at.code = 'PLATFORM'
),

expected AS (
    SELECT
        entry.journal_entry_id,
        tl.account_type,
        tl.register_type,
        tl.direction,
        CASE tl.account_type
            WHEN 'MERCHANT' THEN entry.merchant_account_id
            WHEN 'PLATFORM'
                THEN (SELECT platform_account.account_id FROM platform_account)
            WHEN 'PSP' THEN pd.psp_account_id
            WHEN 'TAX_AUTHORITY'
                THEN
                    CASE
                        WHEN entry.transaction_event_type = 'REFUNDED'
                            THEN cta.account_id
                        ELSE taa.account_id
                    END
        END AS account_id,
        tl.direction
        * CASE tl.amount
            WHEN 'FEE' THEN pf.fee
            WHEN 'GROSS'
                THEN
                    CASE
                        WHEN entry.transaction_event_type = 'REFUNDED'
                            THEN entry.source_quantity
                        ELSE entry.payment_quantity
                    END
            WHEN 'TAX'
                THEN
                    CASE
                        WHEN entry.transaction_event_type = 'REFUNDED'
                            THEN rd.tax_quantity
                        ELSE pd.tax_quantity
                    END
            WHEN 'NET' THEN rd.net_quantity
            WHEN 'MERCHANT_PROCEEDS' THEN pd.net_quantity - pf.fee
        END AS quantity
    FROM entry
    INNER JOIN template_line AS tl
        ON entry.transaction_event_type = tl.transaction_event_type
    LEFT JOIN payment_detail AS pd ON entry.payment_id = pd.transaction_id
    LEFT JOIN refund_detail AS rd
        ON entry.source_transaction_id = rd.transaction_id
    LEFT JOIN payment_fee AS pf ON entry.payment_id = pf.payment_id
    LEFT JOIN tax_authority_account AS taa
        ON pd.shopper_country_id = taa.country_id
    LEFT JOIN capture_tax_authority AS cta
        ON entry.payment_id = cta.payment_id
),

matched AS (
    SELECT
        e.direction,
        e.account_id AS expected_account_id,
        e.quantity AS expected_quantity,
        l.journal_entry_line_id,
        l.account_id,
        l.quantity,
        e.journal_entry_id IS NOT NULL AS has_template_line,
        l.journal_entry_line_id IS NOT NULL AS has_line,
        COALESCE(e.journal_entry_id, l.journal_entry_id) AS journal_entry_id,
        COALESCE(e.register_type, l.register_type) AS register_type
    FROM expected AS e
    FULL OUTER JOIN line AS l
        ON
            e.journal_entry_id = l.journal_entry_id
            AND e.account_type = l.account_type
            AND e.register_type = l.register_type
),

mismatch AS (
    SELECT
        'SOURCE' AS mismatch,
        entry.journal_entry_id,
        NULL::BIGINT AS journal_entry_line_id,
        NULL::TEXT AS register_type
    FROM entry
    LEFT JOIN template_source AS ts
        ON entry.transaction_event_type = ts.transaction_event_type
    WHERE
        ts.journal_entry_type IS DISTINCT FROM entry.journal_entry_type
        OR ts.transaction_type IS DISTINCT FROM entry.transaction_type

    UNION ALL

    SELECT
        'CARDINALITY' AS mismatch,
        entry.journal_entry_id,
        NULL::BIGINT AS journal_entry_line_id,
        NULL::TEXT AS register_type
    FROM entry
    WHERE
        (
            SELECT COUNT(*) FROM line
            WHERE line.journal_entry_id = entry.journal_entry_id
        )
        <> (
            SELECT COUNT(*) FROM template_line AS tl
            WHERE tl.transaction_event_type = entry.transaction_event_type
        )

    UNION ALL

    SELECT
        'ROLE' AS mismatch,
        m.journal_entry_id,
        m.journal_entry_line_id,
        m.register_type
    FROM matched AS m
    WHERE
        NOT m.has_template_line
        OR NOT m.has_line
        OR m.expected_account_id IS DISTINCT FROM m.account_id

    UNION ALL

    SELECT
        'SIGN' AS mismatch,
        m.journal_entry_id,
        m.journal_entry_line_id,
        m.register_type
    FROM matched AS m
    WHERE
        m.has_template_line
        AND m.has_line
        AND m.direction * m.quantity < 0

    UNION ALL

    SELECT
        'FORMULA' AS mismatch,
        m.journal_entry_id,
        m.journal_entry_line_id,
        m.register_type
    FROM matched AS m
    WHERE
        m.has_template_line
        AND m.has_line
        AND m.quantity IS DISTINCT FROM m.expected_quantity

    UNION ALL

    SELECT
        'CURRENCY' AS mismatch,
        entry.journal_entry_id,
        line.journal_entry_line_id,
        line.register_type
    FROM entry
    INNER JOIN line ON entry.journal_entry_id = line.journal_entry_id
    WHERE line.currency_id <> entry.payment_currency_id
)

SELECT
    mismatch.mismatch,
    entry.transaction_reference,
    entry.transaction_event_id,
    entry.transaction_event_type,
    mismatch.journal_entry_id,
    mismatch.journal_entry_line_id,
    mismatch.register_type
FROM mismatch
INNER JOIN entry ON mismatch.journal_entry_id = entry.journal_entry_id
ORDER BY
    mismatch.journal_entry_id,
    mismatch.mismatch,
    mismatch.journal_entry_line_id;
