package rw.ac.rca.cloudsphere.identity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rw.ac.rca.cloudsphere.common.exception.ApiException;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class AuthPolicyTest {

    @Test
    void assertPasswordPolicy_lettersOnly_throws() {
        assertThrows(ApiException.class, () -> AuthService.assertPasswordPolicy("abcdefgh"));
    }

    @Test
    void assertPasswordPolicy_digitsOnly_throws() {
        assertThrows(ApiException.class, () -> AuthService.assertPasswordPolicy("12345678"));
    }

    @Test
    void assertPasswordPolicy_lettersAndDigits_isAccepted() {
        assertDoesNotThrow(() -> AuthService.assertPasswordPolicy("abc12345"));
    }

    @Test
    void isLocked_noLock_returnsFalse() {
        User user = new User();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertFalse(user.isLocked(now));
    }

    @Test
    void hasRole_customer_returnsTrue() {
        User user = new User();
        user.setRoles(Set.of(Role.CUSTOMER));
        assertTrue(user.hasRole(Role.CUSTOMER));
    }

    @Test
    void hasRole_missingAdmin_returnsFalse() {
        User user = new User();
        user.setRoles(Set.of(Role.CUSTOMER));
        assertFalse(user.hasRole(Role.ADMIN));
    }

    @Test
    void isLocked_untilInTheFuture_returnsTrue() {
        User user = new User();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        user.setLockedUntil(now.plusSeconds(30));
        assertTrue(user.isLocked(now));
    }

    @Test
    void isLocked_untilInThePast_returnsFalse() {
        User user = new User();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        user.setLockedUntil(now.minusSeconds(1));
        assertFalse(user.isLocked(now));
    }

    @Test
    void isLocked_statusLocked_returnsTrue() {
        User user = new User();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        user.setStatus(UserStatus.LOCKED);
        assertTrue(user.isLocked(now));
    }
}
