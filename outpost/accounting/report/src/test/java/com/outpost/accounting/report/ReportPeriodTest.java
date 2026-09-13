package com.outpost.accounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportPeriodTest {
  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

  @Test
  void acceptsThirtyInclusiveDays() {
    assertThatCode(() -> new ReportPeriod(FROM, LocalDate.of(2026, 9, 30)))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesThirtyOneInclusiveDays() {
    assertThatThrownBy(() -> new ReportPeriod(FROM, LocalDate.of(2026, 10, 1)))
        .isInstanceOf(InvalidReportPeriodException.class);
  }

  @Test
  void refusesEndBeforeStart() {
    assertThatThrownBy(() -> new ReportPeriod(FROM, LocalDate.of(2026, 8, 31)))
        .isInstanceOf(InvalidReportPeriodException.class);
  }

  @Test
  @SuppressWarnings("NullAway")
  void refusesAnAbsentDate() {
    assertThatThrownBy(() -> new ReportPeriod(null, FROM))
        .isInstanceOf(InvalidReportPeriodException.class);
    assertThatThrownBy(() -> new ReportPeriod(FROM, null))
        .isInstanceOf(InvalidReportPeriodException.class);
  }

  @Test
  void boundsPostedTimesAtMidnightUtcFromTheFirstDayToTheDayAfterTheLast() {
    ReportPeriod period = new ReportPeriod(FROM, LocalDate.of(2026, 9, 3));

    assertThat(period.postedFrom()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(period.postedBefore()).isEqualTo(Instant.parse("2026-09-04T00:00:00Z"));
  }
}
