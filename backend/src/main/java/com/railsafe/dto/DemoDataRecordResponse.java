package com.railsafe.dto;

/**
 * One demonstration/testing row exposed only by the SUPER_ADMIN demo-data API.
 *
 * tableName is the physical table so a Super Admin can see exactly where the row
 * lives; recordLabel is a short human-readable description for the management UI.
 */
public record DemoDataRecordResponse(
        String type,
        Long id,
        String code,
        String tableName,
        String recordLabel,
        String status,
        String detail,
        boolean deletable
) {
}
