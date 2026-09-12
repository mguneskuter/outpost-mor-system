package com.outpost.gateway.order.repository.mybatis;

import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.NewOrder;
import com.outpost.gateway.order.repository.OrderRepository.PersistedOrder;
import com.outpost.gateway.order.service.OrderPhases;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** MyBatis persistence adapter for merchant orders. */
public class MyBatisOrderRepository implements OrderRepository {
  private static final String TRY_PHASE_LOCK = "SELECT pg_try_advisory_lock(CAST(? AS bigint))";
  private static final String RELEASE_PHASE_LOCK = "SELECT pg_advisory_unlock(CAST(? AS bigint))";
  private final OrderMapper mapper;
  private final DataSource dataSource;
  private final ConcurrentMap<UUID, Connection> phaseConnections = new ConcurrentHashMap<>();

  /** Creates an adapter backed by the order mapper. */
  public MyBatisOrderRepository(OrderMapper mapper, DataSource dataSource) {
    this.mapper = mapper;
    this.dataSource = dataSource;
  }

  @Override
  public Optional<Merchant> findMerchant(long accountId) {
    return Optional.ofNullable(mapper.findMerchant(accountId))
        .filter(OrderMapper.MerchantRow::active)
        .map(row -> new Merchant(row.accountId(), row.code()));
  }

  @Override
  public Optional<Psp> findEnabledPsp(long merchantAccountId, String pspCode) {
    return Optional.ofNullable(mapper.findEnabledPsp(merchantAccountId, pspCode))
        .filter(OrderMapper.PspRow::active)
        .map(row -> new Psp(row.accountId(), row.code()));
  }

  @Override
  public boolean hasFeeConfiguration(long merchantAccountId, long currencyId) {
    return mapper.hasFeeConfiguration(merchantAccountId, currencyId);
  }

  @Override
  public @Nullable PersistedOrder findByIdempotency(long merchantAccountId, String idempotencyKey) {
    OrderRow row = mapper.findByIdempotency(merchantAccountId, idempotencyKey);
    return row == null ? null : load(row);
  }

  @Override
  @Transactional
  public @Nullable PersistedOrder insert(NewOrder order) {
    Long shopperId = mapper.insertShopper(order);
    if (shopperId == null) {
      throw new IllegalStateException("shopper was not persisted");
    }
    NewOrder stored =
        new NewOrder(
            order.orderReference(),
            order.merchantReference(),
            order.merchantAccountId(),
            shopperId,
            order.currencyId(),
            order.netAmount(),
            order.taxAmount(),
            order.grossAmount(),
            order.idempotencyKey(),
            order.requestFingerprint(),
            order.paymentReference(),
            order.pspAccountId(),
            order.createdAt(),
            order.shopper(),
            order.lines());
    Long orderId = mapper.insertOrder(stored);
    if (orderId == null) {
      return null;
    }
    for (Line line : order.lines()) {
      if (mapper.insertLine(orderId, line) != 1) {
        throw new IllegalStateException("order line was not persisted");
      }
    }
    if (mapper.insertPayment(orderId, stored) != 1) {
      throw new IllegalStateException("order payment was not persisted");
    }
    return loadRequired(orderId);
  }

  @Override
  @Transactional
  public boolean claimPhase(long orderId, OrderPhases phase, UUID claimToken) {
    Connection connection = openPhaseConnection();
    boolean retained = false;
    try {
      if (!tryPhaseLock(connection, orderId)
          || mapper.claimPhase(orderId, phase.name(), claimToken) != 1) {
        return false;
      }
      if (phaseConnections.putIfAbsent(claimToken, connection) != null) {
        throw new IllegalStateException("phase claim token was already in use");
      }
      retained = true;
      return true;
    } finally {
      if (!retained) {
        closePhaseConnection(connection);
      }
    }
  }

  @Override
  @Transactional
  public void releasePhaseClaim(long orderId, OrderPhases phase, UUID claimToken) {
    try {
      requireUpdated(
          mapper.releasePhaseClaim(orderId, phase.name(), claimToken), orderId, "phase claim");
    } finally {
      releaseAfterTransaction(orderId, claimToken);
    }
  }

  @Override
  @Transactional
  public void markLedgerCreated(long orderId, UUID claimToken) {
    try {
      requireUpdated(mapper.markLedgerCreated(orderId, claimToken), orderId, "Ledger");
    } finally {
      releaseAfterTransaction(orderId, claimToken);
    }
  }

  @Override
  @Transactional
  public void markPspCreated(
      long orderId, UUID claimToken, String pspReference, String paymentLink) {
    try {
      requireUpdated(
          mapper.storePspFacts(orderId, claimToken, pspReference, paymentLink),
          orderId,
          "PSP facts");
      requireUpdated(
          mapper.markPspCreated(orderId, claimToken, pspReference, paymentLink), orderId, "PSP");
    } finally {
      releaseAfterTransaction(orderId, claimToken);
    }
  }

  @Override
  @Transactional
  public void markCompleted(long orderId, UUID claimToken) {
    try {
      requireUpdated(mapper.markCompleted(orderId, claimToken), orderId, "completion");
    } finally {
      releaseAfterTransaction(orderId, claimToken);
    }
  }

  private Connection openPhaseConnection() {
    try {
      return dataSource.getConnection();
    } catch (SQLException exception) {
      throw new IllegalStateException("could not open phase ownership connection", exception);
    }
  }

  private void releaseAfterTransaction(long orderId, UUID claimToken) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      releasePhaseConnection(orderId, claimToken);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            releasePhaseConnection(orderId, claimToken);
          }
        });
  }

  private static boolean tryPhaseLock(Connection connection, long orderId) {
    try (PreparedStatement statement = connection.prepareStatement(TRY_PHASE_LOCK)) {
      statement.setLong(1, orderId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new IllegalStateException("phase ownership query returned no result");
        }
        return result.getBoolean(1);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("could not acquire phase ownership", exception);
    }
  }

  private void releasePhaseConnection(long orderId, UUID claimToken) {
    Connection connection = phaseConnections.remove(claimToken);
    if (connection == null) {
      return;
    }
    try (PreparedStatement statement = connection.prepareStatement(RELEASE_PHASE_LOCK)) {
      statement.setLong(1, orderId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next() || !result.getBoolean(1)) {
          throw new IllegalStateException("phase ownership was not released");
        }
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("could not release phase ownership", exception);
    } finally {
      closePhaseConnection(connection);
    }
  }

  private static void closePhaseConnection(Connection connection) {
    try {
      connection.close();
    } catch (SQLException exception) {
      throw new IllegalStateException("could not close phase ownership connection", exception);
    }
  }

  private PersistedOrder loadRequired(long orderId) {
    OrderRow row = mapper.findById(orderId);
    if (row == null) {
      throw new IllegalStateException("order disappeared: " + orderId);
    }
    return load(row);
  }

  private PersistedOrder load(OrderRow row) {
    return new PersistedOrder(
        row.orderId(),
        row.orderReference(),
        row.merchantReference(),
        row.merchantAccountId(),
        row.merchantCode(),
        row.shopperId(),
        row.shopperCountry(),
        row.shopperCountrySubdivision(),
        row.paymentShopperCountry(),
        row.paymentShopperCountrySubdivision(),
        row.currencyId(),
        row.currency(),
        row.netAmount(),
        row.taxAmount(),
        row.grossAmount(),
        row.idempotencyKey(),
        row.requestFingerprint(),
        row.paymentReference(),
        row.pspAccountId(),
        row.pspCode(),
        row.pspReference(),
        row.paymentLink(),
        row.createdAt(),
        row.phase(),
        mapper.findLines(row.orderId()).stream()
            .map(
                line ->
                    new Line(
                        line.sequence(),
                        line.productTypeId(),
                        line.orderLineReference(),
                        line.merchantLineReference(),
                        line.netAmount(),
                        line.taxAmount(),
                        line.taxRate()))
            .toList());
  }

  private static void requireUpdated(int updated, long orderId, String phase) {
    if (updated != 1) {
      throw new IllegalStateException("could not persist " + phase + " phase for order " + orderId);
    }
  }
}
