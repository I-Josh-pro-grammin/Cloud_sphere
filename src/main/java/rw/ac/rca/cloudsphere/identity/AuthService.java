package rw.ac.rca.cloudsphere.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.rca.cloudsphere.cart.CartService;
import rw.ac.rca.cloudsphere.common.exception.ApiException;
import rw.ac.rca.cloudsphere.config.CloudSphereProperties;
import rw.ac.rca.cloudsphere.security.JwtService;

import java.time.Instant;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_LOGIN_ERROR = "email or password is incorrect";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final CloudSphereProperties properties;
    private final CartService cartService;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService,
                       CloudSphereProperties properties, CartService cartService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.cartService = cartService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request, String guestToken) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists. Try logging in.");
        }
        assertPasswordPolicy(request.password());
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        user.setLoyaltyTier(LoyaltyTier.STANDARD);
        user.setRoles(Set.of(Role.CUSTOMER));
        users.save(user);
        log.info("user_registered email={} inputLen={}", email, request.password().length());
        cartService.mergeGuestCart(user.getId(), guestToken);
        return tokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request, String guestToken) {
        String email = request.email().trim().toLowerCase();
        log.info("login_attempt email={} inputLen={}", email, request.password() == null ? 0 : request.password().length());
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            throw ApiException.unauthorized(GENERIC_LOGIN_ERROR);
        }
        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.DISABLED || user.isLocked(now)) {
            throw ApiException.unauthorized(GENERIC_LOGIN_ERROR);
        }
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= properties.lockout().maxAttempts()) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(now.plus(properties.lockout().duration()));
                log.warn("account_locked email={} attempts={}", email, attempts);
            }
            users.save(user);
            throw ApiException.unauthorized(GENERIC_LOGIN_ERROR);
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        users.save(user);
        cartService.mergeGuestCart(user.getId(), guestToken);
        return tokens(user);
    }

    public TokenResponse tokens(User user) {
        var roles = user.getRoles().stream().map(Enum::name).toList();
        String jwt = jwtService.issue(user.getId(), user.getEmail(), roles);
        return new TokenResponse(jwt, UserResponse.from(user));
    }

    static void assertPasswordPolicy(String password) {
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw ApiException.badRequest("Password must contain at least one letter and one digit.");
        }
    }
}
