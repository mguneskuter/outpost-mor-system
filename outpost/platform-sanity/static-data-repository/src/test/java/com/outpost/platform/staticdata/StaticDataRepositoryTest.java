package com.outpost.platform.staticdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StaticDataRepositoryTest {
  @Test
  void expectedRecordsAreDerivedFromEveryEnumConstantInDeclarationOrder() {
    StaticDataRepository<TestValues, TestValue, TestRecord> repository = new TestRepository();

    assertEquals(
        List.of(new TestRecord(1L, "ONE"), new TestRecord(2L, "TWO")),
        repository.expectedRecords());
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum TestValues {
    ONE(new TestValue(1L, "ONE")),
    TWO(new TestValue(2L, "TWO"));

    private final TestValue value;

    TestValues(TestValue value) {
      this.value = value;
    }
  }

  private record TestValue(long id, String code) {}

  private record TestRecord(long id, String code) {}

  private static final class TestRepository
      implements StaticDataRepository<TestValues, TestValue, TestRecord> {
    @Override
    public Class<TestValues> staticDataEnum() {
      return TestValues.class;
    }

    @Override
    public String table() {
      return "test";
    }

    @Override
    public TestValue enumValue(TestValues constant) {
      return constant.value;
    }

    @Override
    public TestRecord toDatabaseRecord(TestValue value) {
      return new TestRecord(value.id(), value.code());
    }

    @Override
    public TestValue toDomainValue(TestRecord record) {
      return new TestValue(record.id(), record.code());
    }

    @Override
    public long id(TestRecord record) {
      return record.id();
    }

    @Override
    public List<TestRecord> findAll() {
      return List.of();
    }
  }
}
