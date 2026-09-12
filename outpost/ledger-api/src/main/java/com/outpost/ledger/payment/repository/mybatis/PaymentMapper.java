package com.outpost.ledger.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.time.Instant;
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
}
