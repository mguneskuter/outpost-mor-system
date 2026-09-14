package com.outpost.payment.common.repository.sanity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.common.repository.ProductTypeRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductTypeStaticDataRepositoryTest {
  private final RecordingMapper mapper = new RecordingMapper();
  private final ProductTypeStaticDataRepository repository =
      new ProductTypeStaticDataRepository(mapper);

  @Test
  void exposesStaticDataContract() {
    assertEquals(ProductTypes.class, repository.staticDataEnum());
    assertEquals("product_type", repository.table());
  }

  @Test
  void mapsEnumValuesToDatabaseRecords() {
    ProductType value = ProductTypes.DIGITAL_GOODS.getValue();

    assertEquals(new ProductTypeRecord(1L, "DIGITAL_GOODS"), repository.toDatabaseRecord(value));
    assertSame(value, repository.enumValue(ProductTypes.DIGITAL_GOODS));
    assertEquals(1L, repository.id(new ProductTypeRecord(1L, "DIGITAL_GOODS")));
  }

  @Test
  void convertsMatchingDatabaseRecordToCanonicalDomainValue() {
    assertSame(
        ProductTypes.PHYSICAL_GOODS.getValue(),
        repository.toDomainValue(new ProductTypeRecord(2L, "PHYSICAL_GOODS")));
  }

  @Test
  void rejectsDatabaseRecordsThatDoNotMatchTheEnum() {
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.toDomainValue(new ProductTypeRecord(99L, "UNKNOWN")));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.toDomainValue(new ProductTypeRecord(1L, "PHYSICAL_GOODS")));
  }

  @Test
  void delegatesFindAllToTheMapper() {
    List<ProductTypeRecord> records = List.of(new ProductTypeRecord(1L, "DIGITAL_GOODS"));
    mapper.records = records;

    assertSame(records, repository.findAll());
  }

  private static final class RecordingMapper implements ProductTypeStaticDataMapper {
    private List<ProductTypeRecord> records = List.of();

    @Override
    public List<ProductTypeRecord> findAll() {
      return records;
    }
  }
}
