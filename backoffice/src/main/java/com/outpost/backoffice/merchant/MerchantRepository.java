package com.outpost.backoffice.merchant;

import java.util.List;

/** Read-only access to the merchants and PSPs Outpost stores. */
public interface MerchantRepository {
  /** Lists the active merchants, ordered by code. */
  List<Merchant> findActiveMerchants();

  /** Lists the PSPs enabled for the merchant with this code, ordered by PSP code. */
  List<Psp> findEnabledPsps(String merchantCode);

  /** Lists the ISO codes of the countries a shopper may be in, ordered by code. */
  List<String> findCountryCodes();
}
