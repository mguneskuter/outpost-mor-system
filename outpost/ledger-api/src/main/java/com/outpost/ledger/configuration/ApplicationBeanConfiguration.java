package com.outpost.ledger.configuration;

import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.mybatis.MyBatisMerchantFeeConfigurationRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.account.repository.mybatis.MyBatisAccountRepository;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.journalentry.repository.mybatis.MyBatisJournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.accounting.payment.PaymentStateMachine;
import com.outpost.accounting.report.repository.BalanceReportRepository;
import com.outpost.accounting.report.repository.mybatis.BalanceReportMapper;
import com.outpost.accounting.report.repository.mybatis.MyBatisBalanceReportRepository;
import com.outpost.accounting.repository.RegisterRepository;
import com.outpost.accounting.repository.mybatis.MyBatisRegisterRepository;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.accounting.transaction.repository.mybatis.MyBatisTransactionRepository;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.accounting.transactionlock.repository.mybatis.MyBatisTransactionLockRepository;
import com.outpost.accounting.transactionlock.repository.mybatis.TransactionLockMapper;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.queue.QueueProcessor;
import com.outpost.framework.queue.QueueProcessorSettings;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.fx.FxFee;
import com.outpost.fx.provider.FxRateProvider;
import com.outpost.fx.provider.cached.CachedFxRateProvider;
import com.outpost.fx.repository.FxFeeRepository;
import com.outpost.fx.repository.FxRateRepository;
import com.outpost.fx.repository.mybatis.FxFeeMapper;
import com.outpost.fx.repository.mybatis.FxRateMapper;
import com.outpost.fx.repository.mybatis.MyBatisFxFeeRepository;
import com.outpost.fx.repository.mybatis.MyBatisFxRateRepository;
import com.outpost.ledger.accounting.queue.service.AccountingQueueProcessor;
import com.outpost.ledger.accounting.queue.service.AccountingQueueService;
import com.outpost.ledger.accounting.queue.service.LockedAccountingQueueRequest;
import com.outpost.ledger.payment.service.AuthorisationService;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentService;
import com.outpost.ledger.payment.service.RefundService;
import com.outpost.ledger.report.service.BalanceReportService;
import com.outpost.ledger.security.LedgerAuthenticationProperties;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the Ledger's repositories, application services, and accounting queue. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  LedgerAuthenticationProperties.class,
  LedgerAccountingQueueProperties.class
})
public class ApplicationBeanConfiguration {

  /** The in-memory FX rate bound is sized as ordered currency pairs times this many days. */
  private static final int DAYS_OF_RATES_PER_PAIR = 31;

  @Bean
  PaymentFeeCalculator paymentFeeCalculator() {
    return new PaymentFeeCalculator();
  }

  @Bean
  PaymentStateMachine paymentStateMachine() {
    return new PaymentStateMachine();
  }

  @Bean
  AccountRepository accountRepository(SqlSessionTemplate sqlSessionTemplate) {
    return new MyBatisAccountRepository(sqlSessionTemplate);
  }

  @Bean
  RegisterRepository registerRepository(SqlSessionTemplate sqlSessionTemplate) {
    return new MyBatisRegisterRepository(sqlSessionTemplate);
  }

  @Bean
  MerchantFeeConfigurationRepository merchantFeeConfigurationRepository(
      SqlSessionTemplate sqlSessionTemplate) {
    return new MyBatisMerchantFeeConfigurationRepository(sqlSessionTemplate);
  }

  @Bean
  TransactionRepository transactionRepository(
      SqlSessionTemplate sqlSessionTemplate,
      PlatformTransactionManager transactionManager,
      AccountRepository accountRepository) {
    return new MyBatisTransactionRepository(
        sqlSessionTemplate, transactionManager, accountRepository);
  }

  @Bean
  JournalEntryRepository journalEntryRepository(
      SqlSessionTemplate sqlSessionTemplate,
      PlatformTransactionManager transactionManager,
      AccountRepository accountRepository) {
    return new MyBatisJournalEntryRepository(
        sqlSessionTemplate, transactionManager, accountRepository);
  }

  @Bean
  PaymentService paymentService(
      TransactionRepository transactionRepository,
      JournalEntryRepository journalEntryRepository,
      AccountRepository accountRepository,
      RegisterRepository registerRepository,
      MerchantFeeConfigurationRepository merchantFeeConfigurationRepository,
      PaymentFeeCalculator paymentFeeCalculator) {
    return new PaymentService(
        transactionRepository,
        journalEntryRepository,
        accountRepository,
        registerRepository,
        merchantFeeConfigurationRepository,
        paymentFeeCalculator);
  }

  @Bean
  AuthorisationService authorisationService(
      TransactionRepository transactionRepository,
      JournalEntryRepository journalEntryRepository,
      PaymentStateMachine paymentStateMachine) {
    return new AuthorisationService(
        transactionRepository, journalEntryRepository, paymentStateMachine);
  }

  @Bean
  CaptureService captureService(
      TransactionRepository transactionRepository,
      JournalEntryRepository journalEntryRepository,
      AccountRepository accountRepository,
      RegisterRepository registerRepository,
      PaymentStateMachine paymentStateMachine) {
    return new CaptureService(
        transactionRepository,
        journalEntryRepository,
        accountRepository,
        registerRepository,
        paymentStateMachine);
  }

  @Bean
  RefundService refundService(
      TransactionRepository transactionRepository, JournalEntryRepository journalEntryRepository) {
    return new RefundService(transactionRepository, journalEntryRepository);
  }

  @Bean
  TransactionLockRepository transactionLockRepository(TransactionLockMapper mapper) {
    return new MyBatisTransactionLockRepository(mapper);
  }

  @Bean
  TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue(
      LedgerAccountingQueueProperties properties) {
    return new TimeOrderedQueue<>(Clock.systemUTC(), properties.capacity());
  }

  @Bean
  AccountingQueueService accountingRequestService(
      TransactionLockRepository transactionLocks,
      TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue,
      LedgerAccountingQueueProperties properties) {
    return new AccountingQueueService(
        transactionLocks, accountingQueue, properties.transactionLockLease());
  }

  @Bean
  AccountingQueueProcessor accountingQueueProcessor(
      PaymentService paymentService,
      AuthorisationService authorisationService,
      CaptureService captureService,
      RefundService refundService,
      TransactionLockRepository transactionLocks) {
    return new AccountingQueueProcessor(
        paymentService, authorisationService, captureService, refundService, transactionLocks);
  }

  /** The processor never retries a booking, so one attempt is the ceiling. */
  @Bean(initMethod = "start", destroyMethod = "stop")
  QueueProcessor<LockedAccountingQueueRequest> accountingQueueWorkers(
      TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue,
      AccountingQueueProcessor processor,
      LedgerAccountingQueueProperties properties) {
    return new QueueProcessor<>(
        "ledger-accounting-queue",
        accountingQueue,
        processor,
        new QueueProcessorSettings(
            properties.workerCount(), properties.pollInterval(), properties.pollInterval(), 1));
  }

  @Bean
  BalanceReportRepository balanceReportRepository(BalanceReportMapper mapper) {
    return new MyBatisBalanceReportRepository(mapper);
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
    validateFees(List.copyOf(feeRepository.findFxFees()), expectedPairs);
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
        throw new IllegalStateException("Duplicate FX fee pair: " + pair);
      }
    }
    if (!actualPairs.equals(expectedPairs)) {
      throw new IllegalStateException("FX fees must contain exactly one entry per currency pair");
    }
  }

  private record CurrencyPair(Currency baseCurrency, Currency quoteCurrency) {}
}
