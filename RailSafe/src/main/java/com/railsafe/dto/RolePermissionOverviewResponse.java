package com.railsafe.dto;

import java.util.List;

public record RolePermissionOverviewResponse(
        String role,
        String description,
        boolean builtIn,
        List<String> permissions
) {
}
