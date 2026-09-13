package com.outpost.ledger.configuration;

import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.journalentry.repository.mybatis.JournalEntryMapper;
import com.outpost.accounting.journalentry.repository.mybatis.MyBatisJournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxFee;
import com.outpost.fx.provider.FxRateProvider;
import com.outpost.fx.provider.cached.CachedFxRateProvider;
import com.outpost.fx.repository.FxFeeRepository;
import com.outpost.fx.repository.FxRateRepository;
import com.outpost.ledger.fx.repository.mybatis.FxFeeMapper;
import com.outpost.ledger.fx.repository.mybatis.FxRateMapper;
import com.outpost.ledger.fx.repository.mybatis.MyBatisFxFeeRepository;
import com.outpost.ledger.fx.repository.mybatis.MyBatisFxRateRepository;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundReservationService;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import com.outpost.ledger.report.service.BalanceReportService;
import com.outpost.ledger.security.LedgerAuthenticationProperties;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the Ledger's application services and FX persistence. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LedgerAuthenticationProperties.class)
public class ApplicationBeanConfiguration {

  /** The in-memory FX rate bound is sized as ordered currency pairs times this many days. */
  private static final int DAYS_OF_RATES_PER_PAIR = 31;

  @Bean
  Clock ledgerClock() {
    return Clock.systemUTC();
  }

  @Bean
  PaymentFeeCalculator paymentFeeCalculator() {
    return new PaymentFeeCalculator();
  }

  @Bean
  JournalEntryRepository journalEntryRepository(
      JournalEntryMapper mapper, PlatformTransactionManager transactionManager) {
    return new MyBatisJournalEntryRepository(mapper, transactionManager);
  }

  @Bean
  PaymentCreationService paymentCreationService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentFeeCalculator calculator,
      Clock clock) {
    return new PaymentCreationService(repository, journalEntryRepository, calculator, clock);
  }

  @Bean
  PaymentEventService paymentEventService(
      PaymentRepository repository, JournalEntryRepository journalEntryRepository, Clock clock) {
    return new PaymentEventService(repository, journalEntryRepository, clock);
  }

  @Bean
  CaptureService captureService(
      PaymentRepository repository, JournalEntryRepository journalEntryRepository, Clock clock) {
    return new CaptureService(repository, journalEntryRepository, clock);
  }

  @Bean
  RefundReservationService refundReservationService(PaymentRepository repository, Clock clock) {
    return new RefundReservationService(repository, clock);
  }

  @Bean
  BalanceReportService balanceReportService(BalanceReportRepository repository) {
    return new BalanceReportService(repository);
  }

  /** Creates the repository that converts persisted rate rows to domain rates. */
  @Bean
  FxRateRepository fxRateRepository(FxRateMapper mapper) {
    return new MyBatisFxRateRepository(mapper);
  }

  /** Creates the repository that converts persisted fee rows to domain fees. */
  @Bean
  FxFeeRepository fxFeeRepository(FxFeeMapper mapper) {
    return new MyBatisFxFeeRepository(mapper);
  }

  /**
   * Validates the FX fees, then returns a provider that reads each requested rate from the
   * database.
   *
   * @throws IllegalStateException when the fees do not cover every ordered currency pair
   */
  @Bean
  @DependsOn("com.outpost.platform.staticdata.check.SystemSanityCheck")
  FxRateProvider fxRateProvider(FxRateRepository rateRepository, FxFeeRepository feeRepository) {
    Set<CurrencyPair> expectedPairs = expectedPairs();
    validateFees(List.copyOf(feeRepository.findAll()), expectedPairs);
    return new CachedFxRateProvider(rateRepository, expectedPairs.size() * DAYS_OF_RATES_PER_PAIR);
  }

  private static Set<CurrencyPair> expectedPairs() {
    Set<CurrencyPair> pairs = new HashSet<>();
    for (Currencies base : Currencies.values()) {
      for (Currencies quote : Currencies.values()) {
        if (base != quote) {
          pairs.add(new CurrencyPair(base.getValue(), quote.getValue()));
        }
      }
    }
    return Set.copyOf(pairs);
  }

  private static void validateFees(List<FxFee> fees, Set<CurrencyPair> expectedPairs) {
    Set<CurrencyPair> actualPairs = new HashSet<>();
    for (FxFee fee : fees) {
      CurrencyPair pair = new CurrencyPair(fee.baseCurrency(), fee.quoteCurrency());
      if (!actualPairs.add(pair)) {
        throw new IllegalStateException("duplicate FX fee pair: " + pair);
      }
    }
    if (!actualPairs.equals(expectedPairs)) {
      throw new IllegalStateException("FX fees must contain exactly one entry per currency pair");
    }
  }

  private record CurrencyPair(Currency baseCurrency, Currency quoteCurrency) {}
}
