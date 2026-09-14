package com.outpost.gateway.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.report.BalanceReport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratedReportsTest {
  private static final int CAPACITY = 100;

  @Test
  void keepsEveryReportUpToCapacityAndEvictsTheOldestBeyondIt() {
    GeneratedReports reports = new GeneratedReports(CAPACITY);
    List<UUID> reportIds = new ArrayList<>();
    for (int day = 1; day <= CAPACITY + 1; day++) {
      reportIds.add(reports.add(report(day)));
    }

    assertThat(reports.find(reportIds.getFirst())).isEmpty();
    for (int index = 1; index <= CAPACITY; index++) {
      assertThat(reports.find(reportIds.get(index))).contains(report(index + 1));
    }
  }

  @Test
  void answersEmptyForAnIdentifierNeverAdded() {
    assertThat(new GeneratedReports(CAPACITY).find(UUID.randomUUID())).isEmpty();
  }

  private static BalanceReport report(int day) {
    LocalDate date = LocalDate.of(2026, 1, 1).plusDays(day - 1);
    return new BalanceReport(date, date, List.of());
  }
}
