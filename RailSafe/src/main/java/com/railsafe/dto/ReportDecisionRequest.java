package com.railsafe.dto;

import jakarta.validation.constraints.Size;

public record ReportDecisionRequest(
        @Size(max = 300, message = "Review note is too long")
        String reviewNote
) {
}
