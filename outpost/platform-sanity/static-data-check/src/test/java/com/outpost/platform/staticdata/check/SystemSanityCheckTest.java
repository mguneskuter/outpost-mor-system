package com.outpost.platform.staticdata.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SystemSanityCheckTest {

  @Test
  void rejectsDivergentRows() {
    StaticDataRepository<?, ?, ?> repository = new Repository(List.of(new TestRecord(1, "WRONG")));

    SystemSanityCheck verifier = new SystemSanityCheck(List.of(repository));

    assertThatThrownBy(verifier::verify)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("divergent row");
  }

  @Test
  void rejectsMissingRows() {
    assertThatThrownBy(() -> new SystemSanityCheck(List.of(new Repository(List.of()))).verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing row");
  }

  @Test
  void rejectsUnexpectedRows() {
    assertThatThrownBy(
            () ->
                new SystemSanityCheck(
                        List.of(new Repository(List.of(new TestRecord(99, "UNEXPECTED")))))
                    .verify())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unexpected row");
  }

  @Test
  void acceptsMatchingRows() {
    new SystemSanityCheck(List.of(new Repository(List.of(new TestRecord(1, "ONE"))))).verify();
  }

  @Test
  void verificationOnlyReadsRepositories() {
    AtomicInteger reads = new AtomicInteger();
    StaticDataRepository<?, ?, ?> repository = new ReadOnlyRepository(reads);

    new SystemSanityCheck(List.of(repository)).verify();

    assertThat(reads.get()).isEqualTo(1);
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum TestValues {
    ONE(new TestValue(1, "ONE"));

    private final TestValue value;

    TestValues(TestValue value) {
      this.value = value;
    }
  }

  private record TestValue(long id, String code) {}

  private record TestRecord(long id, String code) {}

  private static final class Repository
      implements StaticDataRepository<TestValues, TestValue, TestRecord> {
    private final List<TestRecord> actual;

    private Repository(List<TestRecord> actual) {
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
      return actual;
    }
  }

  private static final class ReadOnlyRepository
      implements StaticDataRepository<TestValues, TestValue, TestRecord> {
    private final AtomicInteger reads;

    private ReadOnlyRepository(AtomicInteger reads) {
      this.reads = reads;
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
      reads.incrementAndGet();
      return List.of(new TestRecord(1, "ONE"));
    }
  }
}
