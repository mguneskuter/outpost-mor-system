package com.outpost.platform.staticdata.job;

import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.HashMap;
import java.util.Map;

/** Inserts absent expected records and fails closed when a database record diverges. */
public final class EnsureStaticDataOperator<E extends Enum<E>, V, R> {
  private final StaticDataRepository<E, V, R> repository;
  private final Inserter<R> inserter;

  /** Creates a seeder for one typed repository and its matching insert mapper. */
  public EnsureStaticDataOperator(StaticDataRepository<E, V, R> repository, Inserter<R> inserter) {
    this.repository = repository;
    this.inserter = inserter;
  }

  /** Applies the absent expected records. */
  public void ensure() {
    Map<Long, R> actual = new HashMap<>();
    for (R record : repository.findAll()) {
      actual.put(repository.id(record), record);
    }

    for (R expected : repository.expectedRecords()) {
      long id = repository.id(expected);
      R databaseRecord = actual.get(id);
      if (databaseRecord == null) {
        inserter.insert(expected);
      } else if (!databaseRecord.equals(expected)) {
        throw new IllegalStateException(
            "%s record %d diverged: expected=%s actual=%s"
                .formatted(repository.table(), id, expected, databaseRecord));
      }
    }
  }

  /** Inserts one typed record owned by the job. */
  public interface Inserter<R> {
    /** Inserts one record. */
    void insert(R record);
  }
}
