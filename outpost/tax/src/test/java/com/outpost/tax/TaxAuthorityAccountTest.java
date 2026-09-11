package com.outpost.tax;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Countries;
import org.junit.jupiter.api.Test;

class TaxAuthorityAccountTest {

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCountry() {
    assertThatThrownBy(() -> new TaxAuthorityAccount(null, 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNonPositiveAccountId() {
    assertThatThrownBy(() -> new TaxAuthorityAccount(Countries.NETHERLANDS.getValue(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TaxAuthorityAccount(Countries.NETHERLANDS.getValue(), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
