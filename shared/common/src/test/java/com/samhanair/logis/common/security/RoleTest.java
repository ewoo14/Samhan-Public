package com.samhanair.logis.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void hasSevenRoles() {
        assertEquals(7, Role.values().length);
    }

    @Test
    void masterDisplayNameIsKorean() {
        assertEquals("마스터", Role.MASTER.getDisplayName());
    }

    @Test
    void fromAuthorityResolvesSales() {
        assertEquals(Role.SALES, Role.fromAuthority("ROLE_SALES"));
    }

    @Test
    void fromAuthorityRejectsBareName() {
        assertThrows(IllegalArgumentException.class, () -> Role.fromAuthority("BOGUS"));
    }

    @Test
    void fromAuthorityRejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class, () -> Role.fromAuthority("ROLE_NOPE"));
    }

    @Test
    void fromAuthorityRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Role.fromAuthority(null));
    }
}
