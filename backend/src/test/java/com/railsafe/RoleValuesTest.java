package com.railsafe;

import com.railsafe.entity.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the four requested roles while preserving the legacy commuter role. */
class RoleValuesTest {

    @Test
    void supportsFourRolesAndPreservesLegacyUser() {
        Set<String> roles = java.util.Arrays.stream(Role.values())
                .map(Role::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("SUPER_ADMIN", "ADMIN", "STAFF", "CUSTOMER", "USER"), roles);
    }
}
