package com.outpost.account.configuration.repository.sanity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.account.configuration.repository.FeeModeRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeModeStaticDataRepositoryTest {
  private final RecordingMapper mapper = new RecordingMapper();
  private final FeeModeStaticDataRepository repository = new FeeModeStaticDataRepository(mapper);

  @Test
  void exposesStaticDataContract() {
    assertEquals(FeeModes.class, repository.staticDataEnum());
    assertEquals("fee_mode", repository.table());
  }

  @Test
  void mapsEnumValuesToDatabaseRecords() {
    FeeMode value = FeeModes.PERCENTAGE.getValue();

    assertEquals(new FeeModeRecord(1L, "PERCENTAGE"), repository.toDatabaseRecord(value));
    assertSame(value, repository.enumValue(FeeModes.PERCENTAGE));
    assertEquals(1L, repository.id(new FeeModeRecord(1L, "PERCENTAGE")));
  }

  @Test
  void convertsMatchingDatabaseRecordToCanonicalDomainValue() {
    assertSame(
        FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
        repository.toDomainValue(new FeeModeRecord(2L, "PERCENTAGE_PLUS_FIXED")));
  }

  @Test
  void rejectsDatabaseRecordsThatDoNotMatchTheEnum() {
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.toDomainValue(new FeeModeRecord(99L, "UNKNOWN")));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.toDomainValue(new FeeModeRecord(1L, "PERCENTAGE_PLUS_FIXED")));
  }

  @Test
  void delegatesFindAllToTheMapper() {
    List<FeeModeRecord> records = List.of(new FeeModeRecord(1L, "PERCENTAGE"));
    mapper.records = records;

    assertSame(records, repository.findAll());
  }

  private static final class RecordingMapper implements FeeModeStaticDataMapper {
    private List<FeeModeRecord> records = List.of();

    @Override
    public List<FeeModeRecord> findAll() {
      return records;
    }
  }
}
