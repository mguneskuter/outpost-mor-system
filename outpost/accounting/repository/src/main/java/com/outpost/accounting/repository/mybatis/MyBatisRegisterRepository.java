package com.outpost.accounting.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.repository.RegisterRepository;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;

/** Reads registers from {@code register}. */
public final class MyBatisRegisterRepository implements RegisterRepository {
  private final RegisterMapper mapper;

  /** Creates a repository over a Spring-managed {@code sqlSession}. */
  public MyBatisRegisterRepository(SqlSession sqlSession) {
    this.mapper = sqlSession.getMapper(RegisterMapper.class);
  }

  @Override
  public Optional<Register> findRegisterByAccountAndRegisterType(
      Account account, RegisterType registerType) {
    return Optional.ofNullable(
            mapper.findRegisterIdByAccountAndRegisterType(
                account.getAccountId(), registerType.getCode()))
        .map(registerId -> new Register(registerId, account, registerType));
  }
}
