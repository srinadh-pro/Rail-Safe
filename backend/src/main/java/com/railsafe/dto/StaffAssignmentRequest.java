package com.railsafe.dto;

import java.util.List;

/** Complete replacement set of gates assigned to one staff account. */
public record StaffAssignmentRequest(List<Long> gateIds) {
}
