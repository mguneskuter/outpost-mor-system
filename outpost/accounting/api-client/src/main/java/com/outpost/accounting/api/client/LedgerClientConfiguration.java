package com.outpost.accounting.api.client;

import com.outpost.accounting.api.AccountingQueueApi;
import com.outpost.accounting.api.BalanceReportApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link AccountingQueueApi} and {@link BalanceReportApi} proxies as beans. The importing
 * application declares a {@link LedgerHttpServiceGroupConfigurer} bean, without which the proxies
 * have no Ledger address or signature.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(
    group = LedgerClientConfiguration.GROUP,
    types = {AccountingQueueApi.class, BalanceReportApi.class})
public class LedgerClientConfiguration {
  static final String GROUP = "ledger";
}
