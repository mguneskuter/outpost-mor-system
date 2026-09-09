package com.outpost.platform.staticdata;

import java.util.Arrays;
import java.util.List;

/** Read-only static-data contract mapping one annotated enum to one typed database record. */
public interface StaticDataRepository<E extends Enum<E>, V, R> {

  /** Returns the annotated enum served by this repository. */
  Class<E> staticDataEnum();

  /** Returns the table name used in lifecycle diagnostics. */
  String table();

  /** Returns the enum-owned domain value. */
  V enumValue(E constant);

  /** Returns the database record projected from an enum-owned value. */
  R toDatabaseRecord(V value);

  /** Returns the exact enum-owned value represented by a database record. */
  V toDomainValue(R record);

  /** Returns the stable database identifier of a record. */
  long id(R record);

  /** Returns every database row as a typed record. */
  List<R> findAll();

  /** Returns every expected database record derived from the enum. */
  default List<R> expectedRecords() {
    return Arrays.stream(staticDataEnum().getEnumConstants())
        .map(this::enumValue)
        .map(this::toDatabaseRecord)
        .toList();
  }
}
