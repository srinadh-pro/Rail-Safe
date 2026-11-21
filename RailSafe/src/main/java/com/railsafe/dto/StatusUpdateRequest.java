package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        GateStatus status,

        StatusSource source,

        VerificationStatus verificationStatus,

        @Size(max = 300, message = "Note is too long")
        String note
) {
}
