package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "Gate ID is required")
        Long gateId,

        @NotNull(message = "Reported status is required")
        GateStatus reportedStatus,

        @Size(max = 500, message = "Comment is too long")
        String comment
) {
}
