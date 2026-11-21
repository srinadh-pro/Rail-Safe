package com.railsafe.dto;

import java.util.List;

public record UpdateRolePermissionsRequest(List<String> permissions) {
}
