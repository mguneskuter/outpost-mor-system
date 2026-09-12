package com.outpost.payment.repository.sanity;

import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventCodes.PspEventCode;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventResults.PspEventResult;
import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.PspEventStatuses.PspEventStatus;
import com.outpost.payment.repository.PspEventCodeRecord;
import com.outpost.payment.repository.PspEventResultRecord;
import com.outpost.payment.repository.PspEventStatusRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers PSP event static-data repositories. */
@Configuration(proxyBeanMethods = false)
public class PspEventStaticDataRepositories {
  /** Creates the PSP event code repository. */
  @Bean
  public StaticDataRepository<PspEventCodes, PspEventCode, PspEventCodeRecord>
      pspEventCodeRepository(PspEventCodeStaticDataMapper m) {
    return new PspEventCodeStaticDataRepository(m);
  }

  /** Creates the PSP event status repository. */
  @Bean
  public StaticDataRepository<PspEventStatuses, PspEventStatus, PspEventStatusRecord>
      pspEventStatusRepository(PspEventStatusStaticDataMapper m) {
    return new PspEventStatusStaticDataRepository(m);
  }

  /** Creates the PSP event result repository. */
  @Bean
  public StaticDataRepository<PspEventResults, PspEventResult, PspEventResultRecord>
      pspEventResultRepository(PspEventResultStaticDataMapper m) {
    return new PspEventResultStaticDataRepository(m);
  }
}
