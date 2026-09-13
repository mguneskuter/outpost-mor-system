package com.outpost.gateway.psp.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.GatewayPrincipalArgumentResolver;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PspControllerTest {
  private static final long MERCHANT_ACCOUNT_ID = 10L;
  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new PspController(new EnabledPsps()))
          .setCustomArgumentResolvers(new GatewayPrincipalArgumentResolver())
          .build();

  @Test
  void listsTheCallersEnabledPsps() throws Exception {
    mockMvc
        .perform(
            get("/v1/psps")
                .requestAttr(
                    MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                    GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"psps":[{"code":"ALPHA_PSP","name":"Alpha PSP"},
                             {"code":"BETA_PSP","name":"Beta PSP"}]}
                    """));
  }

  @Test
  void listsNothingForCallerWithoutEnabledPsps() throws Exception {
    mockMvc
        .perform(
            get("/v1/psps")
                .requestAttr(
                    MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                    GatewayPrincipal.merchant(MERCHANT_ACCOUNT_ID + 1)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"psps\":[]}"));
  }

  private static final class EnabledPsps implements MerchantPspRepository {
    @Override
    public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Account> findEnabledPsps(long merchantAccountId) {
      if (merchantAccountId != MERCHANT_ACCOUNT_ID) {
        return List.of();
      }
      return List.of(psp(20L, "ALPHA_PSP", "Alpha PSP"), psp(21L, "BETA_PSP", "Beta PSP"));
    }

    private static Account psp(long accountId, String code, String name) {
      return Account.of(accountId, AccountTypes.PSP.getValue(), code, name, true, CREATED, ROOT);
    }
  }
}
