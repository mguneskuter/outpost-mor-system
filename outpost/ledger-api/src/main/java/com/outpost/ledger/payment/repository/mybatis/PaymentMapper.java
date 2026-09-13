package com.outpost.ledger.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis statements for the payment creation transaction. */
@RegisteredMapper
public interface PaymentMapper {
  /** Reads the stored idempotency fingerprint. */
  @Select(
      """
      SELECT t.transaction_id, t.account_id, t.reference, t.quantity, t.currency_id, t.created_ts,
             pd.psp_account_id, pd.shopper_country_id, pd.shopper_country_subdivision_id,
             pd.net_quantity, pd.tax_quantity
        FROM transaction t JOIN payment_detail pd USING (transaction_id)
       WHERE t.reference = #{reference}
      """)
  ExistingPaymentRow findByReference(@Param("reference") String reference);

  /** Reads an account by code. */
  @Select(
      """
      SELECT a.account_id, a.account_type_id, a.code, a.name, a.is_active, a.created_ts,
             a.parent_account_id, p.account_type_id parent_type_id, p.code parent_code,
             p.name parent_name, p.is_active parent_active, p.created_ts parent_created_ts
        FROM account a LEFT JOIN account p ON p.account_id = a.parent_account_id
       WHERE a.code = #{code}
      """)
  AccountRow findAccount(@Param("code") String code);

  /** Reads an account by id. */
  @Select(
      """
      SELECT a.account_id, a.account_type_id, a.code, a.name, a.is_active, a.created_ts,
             a.parent_account_id, p.account_type_id parent_type_id, p.code parent_code,
             p.name parent_name, p.is_active parent_active, p.created_ts parent_created_ts
        FROM account a LEFT JOIN account p ON p.account_id = a.parent_account_id
       WHERE a.account_id = #{id}
      """)
  AccountRow findAccountById(@Param("id") long id);

  /** Reads the merchant fee row. */
  @Select(
      """
      SELECT merchant_fee_configuration_id, account_id, currency_id, fee_mode_id, fee_rate_bps,
             fee_fixed
        FROM merchant_fee_configuration
       WHERE account_id=#{accountId} AND currency_id=#{currencyId}
      """)
  FeeRow findFee(@Param("accountId") long accountId, @Param("currencyId") long currencyId);

  /** Reads the active tax authority account for a country. */
  @Select(
      """
      SELECT a.account_id, a.account_type_id, a.code, a.name, a.is_active, a.created_ts,
             a.parent_account_id, p.account_type_id parent_type_id, p.code parent_code,
             p.name parent_name, p.is_active parent_active, p.created_ts parent_created_ts
        FROM tax_authority_account taa
        JOIN account a ON a.account_id = taa.account_id
        LEFT JOIN account p ON p.account_id = a.parent_account_id
       WHERE taa.country_id = #{countryId}
         AND a.is_active
         AND taa.account_type_id =
             (SELECT account_type_id FROM account_type WHERE code = 'TAX_AUTHORITY')
      """)
  AccountRow findTaxAuthorityAccountByCountryId(@Param("countryId") long countryId);

  /** Reads the platform account. */
  @Select(
      """
      SELECT a.account_id, a.account_type_id, a.code, a.name, a.is_active, a.created_ts,
             a.parent_account_id, p.account_type_id parent_type_id, p.code parent_code,
             p.name parent_name, p.is_active parent_active, p.created_ts parent_created_ts
        FROM account a LEFT JOIN account p ON p.account_id = a.parent_account_id
       WHERE a.code = 'OUTPOST'
      """)
  AccountRow findPlatformAccount();

  /** Inserts a transaction, atomically guarding the unique reference. */
  @Select(
      """
      INSERT INTO transaction (transaction_type_id, account_id, reference, quantity, currency_id,
                               created_ts)
      VALUES ((SELECT transaction_type_id FROM transaction_type WHERE code = 'PAYMENT'),
              #{merchantId},#{reference},#{gross},#{currencyId},#{createdAt})
      ON CONFLICT (reference) DO NOTHING RETURNING transaction_id
      """)
  Long insertTransaction(
      @Param("merchantId") long merchantId,
      @Param("reference") String reference,
      @Param("gross") long gross,
      @Param("currencyId") long currencyId,
      @Param("createdAt") Instant createdAt);

  /** Inserts payment detail. */
  @Insert(
      """
      INSERT INTO payment_detail (transaction_id, transaction_type_id, shopper_country_id,
                                  shopper_country_subdivision_id, psp_account_id, net_quantity,
                                  tax_quantity)
      VALUES (#{transactionId},
              (SELECT transaction_type_id FROM transaction_type WHERE code = 'PAYMENT'),
              #{countryId},#{subdivisionId},#{pspId},#{net},#{tax})
      """)
  void insertPaymentDetail(
      @Param("transactionId") long transactionId,
      @Param("countryId") long countryId,
      @Param("subdivisionId") Long subdivisionId,
      @Param("pspId") long pspId,
      @Param("net") long net,
      @Param("tax") long tax);

  /** Inserts the order-created event. */
  @Select(
      """
      INSERT INTO transaction_event (transaction_id, transaction_event_type_id, event_ts)
      VALUES (#{transactionId},
              (SELECT transaction_event_type_id FROM transaction_event_type
                WHERE code = 'ORDER_CREATED'),
              #{at})
      RETURNING transaction_event_id
      """)
  long insertEvent(@Param("transactionId") long transactionId, @Param("at") Instant at);

  /** Locks the payment family root before reading or appending lifecycle evidence. */
  @Select(
      """
      SELECT root.transaction_id, root.currency_id, root.account_id merchant_account_id,
             pd.psp_account_id, pd.shopper_country_id, root.quantity gross_quantity,
             pd.net_quantity, pd.tax_quantity
        FROM transaction target
        JOIN transaction root
          ON root.transaction_id = COALESCE(target.parent_transaction_id, target.transaction_id)
        JOIN payment_detail pd ON pd.transaction_id = root.transaction_id
        JOIN transaction_type payment_type ON payment_type.code = 'PAYMENT'
       WHERE target.reference = #{reference}
         AND target.transaction_type_id = payment_type.transaction_type_id
         AND root.transaction_type_id = payment_type.transaction_type_id
       FOR UPDATE OF root
      """)
  PaymentFamilyRow findPaymentFamilyForUpdate(@Param("reference") String reference);

  /** Reads payment events in append order for the lifecycle fold. */
  @Select(
      """
      SELECT transaction_event_id, transaction_event_type_id, event_ts
        FROM transaction_event
       WHERE transaction_id = #{transactionId}
       ORDER BY transaction_event_id
      """)
  List<PaymentEventRow> findPaymentEvents(@Param("transactionId") long transactionId);

  /** Reads the single capture child and its capture event type. */
  @Select(
      """
      SELECT child.transaction_id, child.reference, child.quantity, child.currency_id,
             child.created_ts, capture_event.transaction_event_type_id event_type_id
        FROM transaction child
        LEFT JOIN transaction_event capture_event ON capture_event.transaction_id = child.transaction_id
       WHERE child.parent_transaction_id = #{paymentTransactionId}
         AND child.transaction_type_id =
             (SELECT transaction_type_id FROM transaction_type WHERE code = 'CAPTURE')
       ORDER BY child.transaction_id
       LIMIT 1
      """)
  CaptureChildRow findCaptureChild(@Param("paymentTransactionId") long paymentTransactionId);

  /** Reads a CAPTURE child by its unique reference. */
  @Select(
      """
      SELECT child.transaction_id, child.reference, child.quantity, child.currency_id,
             child.created_ts, capture_event.transaction_event_type_id event_type_id
        FROM transaction child
        LEFT JOIN transaction_event capture_event ON capture_event.transaction_id = child.transaction_id
       WHERE child.reference = #{reference}
         AND child.transaction_type_id =
             (SELECT transaction_type_id FROM transaction_type WHERE code = 'CAPTURE')
      """)
  CaptureChildRow findCaptureByReference(@Param("reference") String reference);

  /** Reads a register and its owning account type. */
  @Select(
      """
      SELECT r.register_id, r.account_id, a.account_type_id, r.register_type_id
        FROM register r JOIN account a USING (account_id)
       WHERE r.account_id = #{accountId} AND r.register_type_id = #{registerTypeId}
      """)
  RegisterRow findRegister(
      @Param("accountId") long accountId, @Param("registerTypeId") long registerTypeId);

  /** Inserts a CAPTURE child transaction, atomically guarding its reference. */
  @Select(
      """
      INSERT INTO transaction (transaction_type_id, parent_transaction_id, account_id, reference,
                               quantity, currency_id, created_ts)
      VALUES ((SELECT transaction_type_id FROM transaction_type WHERE code = 'CAPTURE'),
              #{paymentTransactionId},#{merchantAccountId},#{reference},#{amount},#{currencyId},
              #{createdAt})
      ON CONFLICT (reference) DO NOTHING RETURNING transaction_id
      """)
  Long insertCaptureTransaction(
      @Param("paymentTransactionId") long paymentTransactionId,
      @Param("merchantAccountId") long merchantAccountId,
      @Param("reference") String reference,
      @Param("amount") long amount,
      @Param("currencyId") long currencyId,
      @Param("createdAt") Instant createdAt);

  /** Appends a payment lifecycle event while retaining the database idempotency guard. */
  @Select(
      """
      INSERT INTO transaction_event (transaction_id, transaction_event_type_id, event_ts)
      VALUES (#{transactionId},#{eventTypeId},#{occurredAt})
      ON CONFLICT (transaction_id, transaction_event_type_id) DO NOTHING
      RETURNING transaction_event_id
      """)
  Long insertPaymentEvent(
      @Param("transactionId") long transactionId,
      @Param("eventTypeId") long eventTypeId,
      @Param("occurredAt") Instant occurredAt);

  /** Reads the merchant and platform pending-fee lines for the payment. */
  @Select(
      """
      SELECT ABS(merchant_line.quantity) fee, merchant_line.currency_id,
             merchant_line.register_id merchant_register_id,
             platform_line.register_id platform_register_id
        FROM transaction t
        JOIN transaction_event te ON te.transaction_id = t.transaction_id
        JOIN transaction_event_type tet
          ON tet.transaction_event_type_id = te.transaction_event_type_id
        JOIN journal_entry je ON je.transaction_event_id = te.transaction_event_id
        JOIN journal_entry_type jet
          ON jet.journal_entry_type_id = je.journal_entry_type_id
        JOIN journal_entry_line merchant_line ON merchant_line.journal_entry_id = je.journal_entry_id
        JOIN register merchant_register ON merchant_register.register_id = merchant_line.register_id
        JOIN account merchant_account ON merchant_account.account_id = merchant_register.account_id
        JOIN account_type merchant_type ON merchant_type.account_type_id = merchant_account.account_type_id
        JOIN register_type merchant_register_type
          ON merchant_register_type.register_type_id = merchant_register.register_type_id
        JOIN journal_entry_line platform_line
          ON platform_line.journal_entry_id = je.journal_entry_id
         AND platform_line.currency_id = merchant_line.currency_id
        JOIN register platform_register ON platform_register.register_id = platform_line.register_id
        JOIN account platform_account ON platform_account.account_id = platform_register.account_id
        JOIN account_type platform_type ON platform_type.account_type_id = platform_account.account_type_id
        JOIN register_type platform_register_type
          ON platform_register_type.register_type_id = platform_register.register_type_id
       WHERE t.transaction_id = #{transactionId}
         AND tet.code = 'ORDER_CREATED'
         AND jet.code = 'FEE_PENDING'
         AND merchant_account.account_id = t.account_id
         AND merchant_type.code = 'MERCHANT'
         AND merchant_register_type.register_type_code = 'PENDING_FEE'
         AND platform_type.code = 'PLATFORM'
         AND platform_register_type.register_type_code = 'PENDING_FEE'
         AND merchant_line.quantity >= 0
      """)
  PendingFeeRow findPendingFee(@Param("transactionId") long transactionId);

  /** Checks the full successful-capture precondition for a refund reservation. */
  boolean hasExactlyOneSuccessfulFullCapture(
      @Param("paymentTransactionId") long paymentTransactionId,
      @Param("gross") long gross,
      @Param("currencyId") long currencyId);

  /** Reads the registers used by the successful CAPTURE entry for refund reversals. */
  CapturePostingRow findCapturePosting(@Param("paymentTransactionId") long paymentTransactionId);

  /** Reads refund children and their latest lifecycle events. */
  List<RefundChildRow> findRefundChildren(@Param("paymentTransactionId") long paymentTransactionId);

  /** Reads a refund child by its unique reference. */
  RefundChildRow findRefundByReference(@Param("reference") String reference);

  /** Inserts a REFUND child transaction, atomically guarding its reference. */
  Long insertRefundTransaction(
      @Param("paymentTransactionId") long paymentTransactionId,
      @Param("merchantAccountId") long merchantAccountId,
      @Param("reference") String reference,
      @Param("gross") long gross,
      @Param("currencyId") long currencyId,
      @Param("createdAt") Instant createdAt);

  /** Inserts refund detail. */
  void insertRefundDetail(
      @Param("transactionId") long transactionId, @Param("net") long net, @Param("tax") long tax);
}
