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

  /** Reads the active country tax authority. */
  @Select(
      """
      SELECT taa.account_id
        FROM tax_authority_account taa JOIN account a ON a.account_id=taa.account_id
       WHERE taa.country_id=#{countryId} AND a.is_active AND taa.account_type_id=5
      """)
  Long findTaxAuthority(@Param("countryId") long countryId);

  /** Reads the platform account. */
  @Select("SELECT account_id FROM account WHERE code='OUTPOST'")
  Long findPlatform();

  /** Reads an account's pending-fee register. */
  @Select("SELECT register_id FROM register WHERE account_id=#{accountId} AND register_type_id=8")
  Long findPendingRegister(@Param("accountId") long accountId);

  /** Inserts a transaction, atomically guarding the unique reference. */
  @Select(
      """
      INSERT INTO transaction (transaction_type_id, account_id, reference, quantity, currency_id,
                               created_ts)
      VALUES (1,#{merchantId},#{reference},#{gross},#{currencyId},#{createdAt})
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
      VALUES (#{transactionId},1,#{countryId},#{subdivisionId},#{pspId},#{net},#{tax})
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
      VALUES (#{transactionId},1,#{at}) RETURNING transaction_event_id
      """)
  long insertEvent(@Param("transactionId") long transactionId, @Param("at") Instant at);

  /** Inserts the pending-fee journal entry. */
  @Select(
      """
      INSERT INTO journal_entry (transaction_event_id, journal_entry_type_id, booked, posted)
      VALUES (#{eventId},3,#{at},#{at}) RETURNING journal_entry_id
      """)
  long insertEntry(@Param("eventId") long eventId, @Param("at") Instant at);

  /** Inserts a journal line. */
  @Select(
      """
      INSERT INTO journal_entry_line (journal_entry_id, register_id, currency_id, quantity)
      VALUES (#{entryId},#{registerId},#{currencyId},#{quantity})
      RETURNING journal_entry_line_id
      """)
  long insertLine(
      @Param("entryId") long entryId,
      @Param("registerId") long registerId,
      @Param("currencyId") long currencyId,
      @Param("quantity") long quantity);

  /** Locks the payment family root before reading or appending lifecycle evidence. */
  @Select(
      """
      SELECT root.transaction_id, root.currency_id
        FROM transaction target
        JOIN transaction root
          ON root.transaction_id = COALESCE(target.parent_transaction_id, target.transaction_id)
       WHERE target.reference = #{reference}
         AND target.transaction_type_id = 1
         AND root.transaction_type_id = 1
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

  /** Inserts the reversal entry whose lines are appended in the same transaction. */
  @Select(
      """
      INSERT INTO journal_entry (transaction_event_id, journal_entry_type_id, booked, posted)
      VALUES (#{eventId},#{entryTypeId},#{at},#{at}) RETURNING journal_entry_id
      """)
  long insertFeeReleaseEntry(
      @Param("eventId") long eventId,
      @Param("entryTypeId") long entryTypeId,
      @Param("at") Instant at);
}
