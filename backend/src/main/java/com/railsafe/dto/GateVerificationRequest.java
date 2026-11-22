package com.railsafe.dto;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Explicit manual verification from STAFF / ADMIN / SUPER_ADMIN.
 *
 * Unlike StatusUpdateRequest, the observed status is optional: when it is
 * omitted the currently recorded status is kept and only the verification
 * state changes. DEMO values are rejected by the service.
 */
public record GateVerificationRequest(
        GateStatus status,

        @NotNull(message = "Verification state is required")
        VerificationStatus verificationStatus,

        @Size(max = 300, message = "Note is too long")
        String note
) {
}
