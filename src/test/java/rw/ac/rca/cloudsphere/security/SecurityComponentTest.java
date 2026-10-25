package rw.ac.rca.cloudsphere.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import rw.ac.rca.cloudsphere.config.CloudSphereProperties;
import rw.ac.rca.cloudsphere.identity.Role;
import rw.ac.rca.cloudsphere.identity.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SecurityComponentTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> values;

    @Test
    void issue_validClaims_roundTrips() {
        JwtService jwt = new JwtService(properties("cloudsphere-test-secret-key-must-be-32b!!"));
        UUID userId = UUID.randomUUID();
        String token = jwt.issue(userId, "a@b.c", List.of("CUSTOMER"));
        Claims claims = jwt.parse(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("a@b.c", claims.get("email", String.class));
        assertTrue(jwt.expiry(claims).isAfter(Instant.now()));
    }

    @Test
    void constructor_shortSecret_throws() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties("too-short")));
    }

    @Test
    void revoke_byExpiry_writesOnlyWhileLive() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey("cloudsphere:revoked:live")).thenReturn(true);
        when(redis.hasKey("cloudsphere:revoked:fresh")).thenReturn(false);
        TokenBlacklist blacklist = new TokenBlacklist(redis);

        blacklist.revoke("expired", Instant.now().minusSeconds(5));
        blacklist.revoke("now", Instant.now());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));

        Instant later = Instant.now().plusSeconds(60);
        blacklist.revoke("live", later);
        verify(values).set(eq("cloudsphere:revoked:live"), eq("1"), any(Duration.class));

        assertTrue(blacklist.isRevoked("live"));
        assertFalse(blacklist.isRevoked("fresh"));
    }

    @Test
    void from_adminUser_exposesAdminFlag() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ops@cloudsphere.rw");
        user.setRoles(Set.of(Role.ADMIN));
        var admin = AuthPrincipal.from(user, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(admin.isAdmin());
        assertEquals("ops@cloudsphere.rw", admin.getUsername());
        assertEquals("", admin.getPassword());
        assertTrue(admin.isAccountNonExpired());
        assertTrue(admin.isAccountNonLocked());
        assertTrue(admin.isCredentialsNonExpired());
        assertTrue(admin.isEnabled());
        assertEquals(admin.authorities(), admin.getAuthorities());

        var customer = new AuthPrincipal(user.getId(), user.getEmail(), List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertFalse(customer.isAdmin());
    }

    private static CloudSphereProperties properties(String secret) {
        return new CloudSphereProperties(
                new CloudSphereProperties.Jwt(secret, "cloudsphere", Duration.ofHours(1)),
                new CloudSphereProperties.Lockout(3, Duration.ofMinutes(30)),
                new CloudSphereProperties.Payment(CloudSphereProperties.StubMode.SUCCESS),
                new CloudSphereProperties.Cors(List.of("http://localhost:5173")),
                new CloudSphereProperties.Seed(true)
        );
    }
}
