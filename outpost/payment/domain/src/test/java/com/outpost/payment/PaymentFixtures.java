package com.outpost.payment;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;

final class PaymentFixtures {
  private PaymentFixtures() {}

  static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  static ShopperDetail shopperWithSubdivision() {
    return new ShopperDetail(
        1L,
        "shopper@example.com",
        "Shopper Name",
        Countries.UNITED_STATES.getValue(),
        CountrySubdivisions.US_CA.getValue(),
        "94105");
  }
}
