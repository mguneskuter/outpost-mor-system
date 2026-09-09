package com.outpost.payment.common.repository;

import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for product types. */
final class ProductTypeStaticDataRepository
    implements StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> {
  private final ProductTypeStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  ProductTypeStaticDataRepository(ProductTypeStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<ProductTypes> staticDataEnum() {
    return ProductTypes.class;
  }

  @Override
  public String table() {
    return "product_type";
  }

  @Override
  public ProductType enumValue(ProductTypes constant) {
    return constant.value();
  }

  @Override
  public ProductTypeRecord toDatabaseRecord(ProductType value) {
    return new ProductTypeRecord(value.productTypeId(), value.code());
  }

  @Override
  public ProductType toDomainValue(ProductTypeRecord record) {
    return ProductTypes.fromCode(record.code())
        .filter(value -> value.productTypeId() == record.productTypeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Product type record does not match the enum: " + record));
  }

  @Override
  public long id(ProductTypeRecord record) {
    return record.productTypeId();
  }

  @Override
  public List<ProductTypeRecord> findAll() {
    return mapper.findAll();
  }
}
