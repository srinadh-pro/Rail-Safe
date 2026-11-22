package com.railsafe.dto;

import java.time.Instant;
import java.util.Map;

/** Aggregate counts for the restricted Super Admin Source Review area only. */
public record SourceReviewSummaryResponse(
        long totalRetainedRows,
        long restrictedImportedRows,
        long gateReviewRows,
        long trainReviewRows,
        long sensorReviewRows,
        Map<String, Integer> categoryCounts,
        Map<String, Integer> gateStatusCounts,
        Map<String, Integer> sensorStatusCounts,
        String dataMode,
        Instant lastImportedAt
) {
}
