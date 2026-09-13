package com.outpost.gateway.report.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP response to a report request: the URL the built report can be read at. */
public record ReportLinkResponse(@JsonProperty("report_url") String reportUrl) {}
