package com.outpost.platform.staticdata.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnsureStaticDataOperatorTest {

  @Test
  void insertsMissingRowsWithoutUpdatingEqualRows() {
    FakeRepository repository = new FakeRepository(List.of());
    EnsureStaticDataOperator<TestValues, Value, TestRecord> operator =
        new EnsureStaticDataOperator<>(repository, record -> repository.inserted.add(record));

    operator.ensure();

    assertThat(repository.inserted)
        .containsExactly(new TestRecord(1, "ONE"), new TestRecord(2, "TWO"));
  }

  @Test
  void failsClosedOnDivergence() {
    FakeRepository repository = new FakeRepository(List.of(new TestRecord(1, "WRONG")));
    EnsureStaticDataOperator<TestValues, Value, TestRecord> operator =
        new EnsureStaticDataOperator<>(repository, record -> repository.inserted.add(record));

    assertThatThrownBy(operator::ensure).isInstanceOf(IllegalStateException.class);
    assertThat(repository.inserted).isEmpty();
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum TestValues {
    ONE(new Value(1, "ONE")),
    TWO(new Value(2, "TWO"));

    private final Value value;

    TestValues(Value value) {
      this.value = value;
    }
  }

  private record Value(long id, String code) {}

  private record TestRecord(long id, String code) {}

  private static final class FakeRepository
      implements StaticDataRepository<TestValues, Value, TestRecord> {
    private final List<TestRecord> actual;
    private final List<TestRecord> inserted = new java.util.ArrayList<>();

    private FakeRepository(List<TestRecord> actual) {
      this.actual = actual;
    }

    @Override
    public Class<TestValues> staticDataEnum() {
      return TestValues.class;
    }

    @Override
    public String table() {
      return "test";
    }

    @Override
    public Value enumValue(TestValues constant) {
      return constant.value;
    }

    @Override
    public TestRecord toDatabaseRecord(Value value) {
      return new TestRecord(value.id(), value.code());
    }

    @Override
    public Value toDomainValue(TestRecord record) {
      return new Value(record.id(), record.code());
    }

    @Override
    public long id(TestRecord record) {
      return record.id();
    }

    @Override
    public List<TestRecord> findAll() {
      return actual;
    }
  }
}
