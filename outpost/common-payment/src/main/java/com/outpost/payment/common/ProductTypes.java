package com.outpost.payment.common;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** The product types supported by Outpost, each owning exactly one value. */
@StaticData
public enum ProductTypes {
  DIGITAL_GOODS(1L, "DIGITAL_GOODS"),
  PHYSICAL_GOODS(2L, "PHYSICAL_GOODS");

  private static final Map<String, ProductTypes> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  productType -> productType.value().code(), productType -> productType));

  @SuppressWarnings("Immutable")
  private final ProductType value;

  ProductTypes(long productTypeId, String code) {
    value = new ProductType(productTypeId, code);
  }

  /** Returns the {@link ProductType} owned by this constant. */
  public ProductType value() {
    return value;
  }

  /** Returns the product type for an exact, case-sensitive code, if any. */
  public static Optional<ProductType> fromCode(String code) {
    Objects.requireNonNull(code, "code");
    return Optional.ofNullable(BY_CODE.get(code)).map(ProductTypes::value);
  }

  /** Immutable product type value owned by one {@link ProductTypes} constant. */
  public static final class ProductType {
    private final long productTypeId;
    private final String code;

    private ProductType(long productTypeId, String code) {
      if (productTypeId <= 0) {
        throw new IllegalArgumentException("productTypeId must be positive: " + productTypeId);
      }
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("code must not be null or blank");
      }
      this.productTypeId = productTypeId;
      this.code = code;
    }

    /** Returns the stable product type identifier. */
    public long productTypeId() {
      return productTypeId;
    }

    /** Returns the exact product type code. */
    public String code() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ProductType that)) {
        return false;
      }
      return productTypeId == that.productTypeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(productTypeId, code);
    }

    @Override
    public String toString() {
      return "ProductType{productTypeId=" + productTypeId + ", code=" + code + '}';
    }
  }
}
