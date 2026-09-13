package com.outpost.gateway.psp.api;

import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.gateway.security.GatewayPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists the PSPs enabled for the authenticated caller. */
@RestController
@RequestMapping("/v1/psps")
public final class PspController {
  private final MerchantPspRepository merchantPsps;

  /** Creates a controller backed by the merchant PSP repository. */
  public PspController(MerchantPspRepository merchantPsps) {
    this.merchantPsps = merchantPsps;
  }

  /** Returns the PSPs the authenticated caller may create orders with. */
  @GetMapping
  public ListPspsResponse list(GatewayPrincipal principal) {
    return ListPspsResponse.from(merchantPsps.findEnabledPsps(principal.accountId()));
  }
}
