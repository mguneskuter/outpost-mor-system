package com.outpost.merchant.cli.merchant;

import java.util.List;
import java.util.Optional;

/** Read-only access to the Outpost rows the shell needs to build its requests. */
public interface MerchantRepository {
  /** Lists the active merchants, ordered by code. */
  List<Merchant> findActiveMerchants();

  /** Lists the PSPs enabled for the merchant with this code, ordered by PSP code. */
  List<Psp> findEnabledPsps(String merchantCode);

  /** Finds the payment facts of an order the PSP has accepted; empty until then. */
  Optional<OrderPayment> findOrderPayment(String orderReference);

  /** Lists what the Ledger booked for an order, oldest first; empty until it booked anything. */
  List<OrderEvent> findOrderEvents(String orderReference);
}
