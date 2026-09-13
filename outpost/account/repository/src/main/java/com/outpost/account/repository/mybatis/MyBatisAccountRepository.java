package com.outpost.account.repository.mybatis;

import com.outpost.account.AccountTypes;
import com.outpost.account.repository.AccountRepository;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;

/** Reads accounts from {@code account}, loading each parent account up to the root. */
public final class MyBatisAccountRepository implements AccountRepository {
  private final AccountMapper mapper;

  /** Creates a repository over a Spring-managed {@code sqlSession}. */
  public MyBatisAccountRepository(SqlSession sqlSession) {
    this.mapper = sqlSession.getMapper(AccountMapper.class);
  }

  @Override
  public Optional<com.outpost.account.Account> findAccountById(long accountId) {
    return Optional.ofNullable(mapper.findAccountById(accountId)).map(this::toAccount);
  }

  @Override
  public Optional<com.outpost.account.Account> findAccountByCode(String code) {
    return Optional.ofNullable(mapper.findAccountByCode(code)).map(this::toAccount);
  }

  private com.outpost.account.Account toAccount(Account row) {
    Long parentAccountId = row.parentAccountId();
    com.outpost.account.Account parent =
        parentAccountId == null
            ? null
            : findAccountById(parentAccountId)
                .orElseThrow(() -> new IllegalStateException("stored parent account is missing"));
    return com.outpost.account.Account.of(
        row.accountId(),
        AccountTypes.fromCode(row.accountType())
            .orElseThrow(() -> new IllegalStateException("stored account type is not supported")),
        row.code(),
        row.name(),
        row.active(),
        row.createdAt(),
        parent);
  }
}
