package com.outpost.persistence.testservices;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A transactional service used to prove that MyBatis queries participate in a Spring-managed
 * transaction.
 */
@Service
public class TransactionalTestService {

  private final TestMapper testMapper;

  /** Creates a service bound to the {@link TestMapper}. */
  public TransactionalTestService(TestMapper testMapper) {
    this.testMapper = testMapper;
  }

  /** Returns whether the query executed inside an active Spring transaction and succeeded. */
  @Transactional
  public boolean runsInTransactionAndQueries() {
    boolean active = TransactionSynchronizationManager.isActualTransactionActive();
    int result = testMapper.one();
    return active && result == 1;
  }
}
