package com.outpost.platform.staticdata.check;

import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;

/** Read-only startup sanity check for every reference-data repository on the classpath. */
public final class SystemSanityCheck implements InitializingBean {

  private final List<StaticDataRepository<?, ?, ?>> repositories;

  /** Creates a read-only verifier over the supplied repositories. */
  public SystemSanityCheck(@Nullable List<StaticDataRepository<?, ?, ?>> repositories) {
    this.repositories = repositories == null ? List.of() : List.copyOf(repositories);
  }

  @Override
  public void afterPropertiesSet() {
    verify();
  }

  /** Fails fast when code-defined reference data differs from its database backend. */
  public void verify() {
    List<String> failures = new ArrayList<>();
    for (StaticDataRepository<?, ?, ?> repository : repositories) {
      verify(repository, failures);
    }
    if (!failures.isEmpty()) {
      throw new IllegalStateException(
          "System sanity check failed for reference data:\n" + String.join("\n", failures));
    }
  }

  private <E extends Enum<E>, V, R> void verify(
      StaticDataRepository<E, V, R> repository, List<String> failures) {
    Map<Long, R> actualRows = new HashMap<>();
    for (R actual : repository.findAll()) {
      actualRows.put(repository.id(actual), actual);
    }
    for (R expected : repository.expectedRecords()) {
      long id = repository.id(expected);
      R actual = actualRows.get(id);
      if (actual == null) {
        failures.add("%s: missing row id=%d".formatted(repository.table(), id));
      } else if (!actual.equals(expected)) {
        failures.add(
            "%s: divergent row id=%d expected=%s actual=%s"
                .formatted(repository.table(), id, expected, actual));
      }
      actualRows.remove(id);
    }
    for (Long id : actualRows.keySet()) {
      failures.add("%s: unexpected row id=%d".formatted(repository.table(), id));
    }
  }
}
