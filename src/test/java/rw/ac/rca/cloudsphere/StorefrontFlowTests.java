package rw.ac.rca.cloudsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import rw.ac.rca.cloudsphere.admin.AdminCatalogService;
import rw.ac.rca.cloudsphere.admin.UpsertProductRequest;
import rw.ac.rca.cloudsphere.catalog.ProductStatus;
import rw.ac.rca.cloudsphere.commerce.DiscountCode;
import rw.ac.rca.cloudsphere.commerce.DiscountCodeRepository;
import rw.ac.rca.cloudsphere.config.DataInitializer;
import rw.ac.rca.cloudsphere.identity.AuthService;
import rw.ac.rca.cloudsphere.identity.LoginRequest;
import rw.ac.rca.cloudsphere.identity.UserRepository;
import rw.ac.rca.cloudsphere.identity.UserStatus;
import rw.ac.rca.cloudsphere.order.OrderRepository;
import rw.ac.rca.cloudsphere.order.OrderService;
import rw.ac.rca.cloudsphere.order.OrderStatus;
import rw.ac.rca.cloudsphere.order.ShopOrder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class StorefrontFlowTests {

    private static final String SECRET = "cloudsphere-test-secret-key-must-be-32b!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("cloudsphere")
            .withUsername("cloudsphere")
            .withPassword("cloudsphere");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("cloudsphere.jwt.secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired UserRepository users;
    @Autowired DiscountCodeRepository discountCodes;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired AdminCatalogService adminCatalog;
    @Autowired DataInitializer dataInitializer;
    @Autowired ApplicationArguments applicationArguments;

    private static String adminToken;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publicSurface_anonymous_responds() throws Exception {
        mvc.perform(get("/").header("X-Trace-Id", "abc123trace"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "abc123trace"))
                .andExpect(jsonPath("$.service").value("cloudsphere-api"))
                .andExpect(jsonPath("$.docs").value("/swagger-ui"));

        mvc.perform(get("/api/v1/health").header("X-Trace-Id", "   "))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        mvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void login_threeBadPasswords_locksTheAccount() throws Exception {
        String email = email("new");
        String guest = newGuestToken();
        String cable = productIdBySku("USB-C-01");
        addItem(null, guest, cable, 1);

        Session session = register(email, "CloudSphere1", "0781111111", guest);
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, auth(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("USB-C-01"))
                .andExpect(header().doesNotExist("X-Cart-Token"));
        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.loyaltyTier").value("STANDARD"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "CloudSphere1", "0781111111")))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email("weak"), "abcdefgh", "0781111111")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email("digits"), "12345678", "0781111111")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email("phone"), "CloudSphere1", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed-json"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@cloudsphere.rw","password":"CloudSphere1"}
                                """))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Cart-Token", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@cloudsphere.rw","password":"CloudSphere1"}
                                """))
                .andExpect(status().isOk());
        authService.login(new LoginRequest("  DEMO@cloudsphere.rw  ", DataInitializer.DEMO_PASSWORD), "  ");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"locked@cloudsphere.rw","password":"CloudSphere1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("email or password is incorrect"));

        assertThrows(rw.ac.rca.cloudsphere.common.exception.ApiException.class,
                () -> authService.login(new LoginRequest("missing-null@cloudsphere.rw", null), "  "));

        String lockEmail = email("lock");
        register(lockEmail, "CloudSphere1", "0782222222", null);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(lockEmail, "WrongPass1")))
                    .andExpect(status().isUnauthorized());
        }
        var locked = users.findByEmailIgnoreCase(lockEmail).orElseThrow();
        assertTrue(locked.getStatus() == UserStatus.LOCKED);
        assertNotNull(locked.getLockedUntil());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(lockEmail, "CloudSphere1")))
                .andExpect(status().isUnauthorized());

        String resetEmail = email("reset");
        register(resetEmail, "CloudSphere1", "0783333333", null);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(resetEmail, "WrongPass1")))
                .andExpect(status().isUnauthorized());
        login(resetEmail, "CloudSphere1", null);
        assertTrue(users.findByEmailIgnoreCase(resetEmail).orElseThrow().getFailedAttempts() == 0);

        var disabled = users.findByEmailIgnoreCase(resetEmail).orElseThrow();
        disabled.setStatus(UserStatus.DISABLED);
        disabled.setLockedUntil(null);
        users.save(disabled);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(resetEmail, "CloudSphere1")))
                .andExpect(status().isUnauthorized());

        String futureEmail = email("future");
        Session future = register(futureEmail, "CloudSphere1", "0784444444", null);
        var futureUser = users.findByEmailIgnoreCase(futureEmail).orElseThrow();
        futureUser.setLockedUntil(Instant.now().plusSeconds(3600));
        users.save(futureUser);
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(futureEmail, "CloudSphere1")))
                .andExpect(status().isUnauthorized());

        users.deleteById(UUID.fromString(future.userId()));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(future.token())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(future.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO"}
                                """))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, auth(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("signed-out"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(session.token())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwt_craftedClaims_passesTheFilter() throws Exception {
        Session session = register(email("jwt"), "CloudSphere1", "0785555555", null);
        String noJti = Jwts.builder()
                .issuer("cloudsphere")
                .subject(session.userId())
                .claim("email", "jwt@cloudsphere.rw")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEY)
                .compact();
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(noJti)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, auth(noJti)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(noJti)))
                .andExpect(status().isOk());

        String noRoles = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("cloudsphere")
                .subject(session.userId())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEY)
                .compact();
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth(noRoles))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth(noRoles))).andExpect(status().isForbidden());

        String wrongIssuer = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("other")
                .subject(session.userId())
                .claim("roles", java.util.List.of("CUSTOMER"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEY)
                .compact();
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, auth(wrongIssuer))).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogue_search_hidesUnpublished() throws Exception {
        mvc.perform(get("/api/v1/products").param("q", "   ").param("page", "-1").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
        mvc.perform(get("/api/v1/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/products").param("q", "does-not-exist").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/products").param("q", "Out of stock").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stockBadge").value("OUT_OF_STOCK"));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Cables')]").exists());
        mvc.perform(get("/api/v1/products/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/" + productIdBySku("HIDDEN-01"))).andExpect(status().isNotFound());
    }

    @Test
    void cart_guest_pricesAndMerges() throws Exception {
        String cable = productIdBySku("USB-C-01");
        String oos = productIdBySku("OOS-01");
        String hidden = productIdBySku("HIDDEN-01");
        String phone = productIdBySku("PHN-01");

        mvc.perform(patch("/api/v1/cart/items/" + cable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qty":1}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/cart/items/" + cable)
                        .header("X-Cart-Token", "missing-guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qty":1}
                                """))
                .andExpect(status().isNotFound());

        String guest = newGuestToken();
        addItem(null, guest, cable, 1);
        addItem(null, guest, cable, 1);
        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", guest))
                .andExpect(jsonPath("$.items[0].qty").value(2));

        mvc.perform(post("/api/v1/cart/items")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","qty":30}
                                """.formatted(cable)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/cart/items")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","qty":1}
                                """.formatted(oos)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/cart/items")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","qty":1}
                                """.formatted(hidden)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/cart/items")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","qty":0}
                                """.formatted(cable)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/cart/items/" + phone)
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qty":1}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/cart/items/" + cable)
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qty":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].qty").value(3));
        mvc.perform(delete("/api/v1/cart/items/" + cable).header("X-Cart-Token", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        addItem(null, guest, cable, 1);
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NO"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NOSUCH"}
                                """))
                .andExpect(status().isBadRequest());

        saveCode("INACT1", 10, 1, false, null, null);
        saveCode("FUTURE1", 10, 1, true, Instant.now().plusSeconds(3600), null);
        saveCode("PASTCD1", 10, 1, true, null, Instant.now().minusSeconds(60));
        saveCode("NEED5", 20, 5, true, null, null);
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"INACT1"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FUTURE1"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PASTCD1"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NEED5"}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"save2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCode").value("SAVE2026"))
                .andExpect(jsonPath("$.discountRwf").value(1_500))
                .andExpect(jsonPath("$.shippingRwf").value(2_000));
        mvc.perform(delete("/api/v1/cart/discount").header("X-Cart-Token", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCode").doesNotExist());

        saveCode("TEMP10", 10, 1, true, null, null);
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TEMP10"}
                                """))
                .andExpect(status().isOk());
        DiscountCode temp = discountCodes.findByCodeIgnoreCase("TEMP10").orElseThrow();
        temp.setActive(false);
        discountCodes.save(temp);
        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", guest))
                .andExpect(jsonPath("$.discountCode").value("TEMP10"))
                .andExpect(jsonPath("$.discountRwf").value(0));

        Session buyer = register(email("buyer"), "CloudSphere1", "0786666666", null);
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SAVE2026"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cart/discount")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SAVE2026"}
                                """))
                .andExpect(status().isBadRequest());

        String priced = createProduct("PRICE01", "Priced Cable", 10_000, 30, "PUBLISHED", "Cables", null);
        addItem(buyer.token(), null, priced, 5);
        mvc.perform(post("/api/v1/cart/discount")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NEED5"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountRwf").value(10_000))
                .andExpect(jsonPath("$.shippingRwf").value(2_000))
                .andExpect(jsonPath("$.totalRwf").value(42_000));

        Session gold = login("gold@cloudsphere.rw", DataInitializer.DEMO_PASSWORD, null);
        String small = createProduct("GOLD01", "Gold Charm", 10_000, 5, "PUBLISHED", null, null);
        addItem(gold.token(), null, small, 1);
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, auth(gold.token())))
                .andExpect(jsonPath("$.pricedAsTier").value("GOLD"))
                .andExpect(jsonPath("$.shippingRwf").value(0))
                .andExpect(jsonPath("$.totalRwf").value(10_000));

        String guestMerge = newGuestToken();
        addItem(null, guestMerge, cable, 1);
        addItem(null, guestMerge, phone, 1);
        mvc.perform(post("/api/v1/cart/discount")
                        .header("X-Cart-Token", guestMerge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"SAVE2026"}
                                """))
                .andExpect(status().isOk());
        Session merged = login("gold@cloudsphere.rw", DataInitializer.DEMO_PASSWORD, guestMerge);
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, auth(merged.token())))
                .andExpect(jsonPath("$.discountCode").value("SAVE2026"))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void checkout_stubbedPayment_keepsOwnership() throws Exception {
        Session buyer = register(email("pay"), "CloudSphere1", "0787777777", null);
        String sku = "PAY" + buyer.userId().substring(0, 6).replace("-", "").toUpperCase();
        String product = createProduct(sku, "Payable " + sku, 12_000, 8, "PUBLISHED", "Cables", "/products/pay.jpg");

        addItem(buyer.token(), null, product, 1);
        JsonNode declined = checkout(buyer.token(), "CARD", "DECLINE", null, null);
        assertTrue(declined.get("status").asText().equals("PLACED"));
        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(jsonPath("$.stockQty").value(8));

        String idempotencyKey = "idem-" + UUID.randomUUID();
        JsonNode timedOut = checkout(buyer.token(), "MTN_MOMO", null, "TIMEOUT", idempotencyKey);
        assertTrue(timedOut.get("status").asText().equals("PAYMENT_TIMED_OUT"));
        String timedOutId = timedOut.get("id").asText();

        JsonNode again = checkout(buyer.token(), "MTN_MOMO", "SUCCESS", null, idempotencyKey);
        assertTrue(again.get("id").asText().equals(timedOutId));
        assertTrue(again.get("status").asText().equals("PAYMENT_TIMED_OUT"));

        mvc.perform(post("/api/v1/orders/" + timedOutId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO","stubMode":"TIMEOUT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_TIMED_OUT"));

        mvc.perform(post("/api/v1/orders/" + timedOutId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","stubMode":"DECLINE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_TIMED_OUT"));

        mvc.perform(post("/api/v1/orders/" + timedOutId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .header("X-Payment-Stub-Mode", "TIMEOUT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","stubMode":"SUCCESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.gatewayRef", startsWith("stub_CARD_")));

        mvc.perform(get("/api/v1/products/" + product))
                .andExpect(jsonPath("$.stockQty").value(7));

        addItem(buyer.token(), null, product, 1);
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO","stubMode":"NOPE"}
                                """))
                .andExpect(status().isBadRequest());

        String blankKey = "   ";
        JsonNode firstBlank = checkout(buyer.token(), "MTN_MOMO", null, null, blankKey);
        assertTrue(firstBlank.get("status").asText().equals("PAID"));
        addItem(buyer.token(), null, product, 1);
        JsonNode secondBlank = checkout(buyer.token(), "MTN_MOMO", "SUCCESS", null, blankKey);
        assertNotEquals(firstBlank.get("id").asText(), secondBlank.get("id").asText());

        mvc.perform(post("/api/v1/orders/" + firstBlank.get("id").asText() + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isConflict());

        Session other = register(email("other"), "CloudSphere1", "0788888888", null);
        mvc.perform(get("/api/v1/orders/" + firstBlank.get("id").asText()).header(HttpHeaders.AUTHORIZATION, auth(other.token())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/" + timedOutId + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","stubMode":"SUCCESS"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, auth(buyer.token())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, auth(buyer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber", startsWith("CS-")));

        String blockedSku = "BLK" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String blocked = createProduct(blockedSku, "Blocked " + blockedSku, 4_000, 4, "PUBLISHED", "Cables", null);
        addItem(buyer.token(), null, blocked, 3);
        mvc.perform(patch("/api/v1/admin/products/" + blocked + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockQty":1}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString(blockedSku)));

        mvc.perform(patch("/api/v1/admin/products/" + blocked)
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(blockedSku, "Blocked " + blockedSku, 4_000, 4, "DRAFT", "Cables", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("no longer available")));

        ShopOrder bare = new ShopOrder();
        bare.setUserId(UUID.fromString(buyer.userId()));
        bare.setOrderNumber("CS-BARE-" + UUID.randomUUID().toString().substring(0, 8));
        bare.setStatus(OrderStatus.PLACED);
        bare.setSubtotalRwf(1_000);
        bare.setDiscountRwf(0);
        bare.setShippingRwf(0);
        bare.setTotalRwf(1_000);
        bare = orderRepository.save(bare);
        mvc.perform(post("/api/v1/orders/" + bare.getId() + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, auth(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","stubMode":"SUCCESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void admin_paidOrder_reachesDelivered() throws Exception {
        Session customer = register(email("wish"), "CloudSphere1", "0789999999", null);
        mvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth(customer.token()))).andExpect(status().isForbidden());

        String cable = productIdBySku("USB-C-01");
        String hidden = productIdBySku("HIDDEN-01");
        String oos = productIdBySku("OOS-01");
        mvc.perform(post("/api/v1/wishlist/" + cable).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].available").value(true));
        mvc.perform(post("/api/v1/wishlist/" + cable).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/wishlist/" + hidden).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/wishlist/" + oos).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/wishlist/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/wishlist/" + cable + "/move-to-cart").header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("USB-C-01"));
        mvc.perform(delete("/api/v1/wishlist/" + cable).header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/v1/wishlist/" + cable + "/move-to-cart").header(HttpHeaders.AUTHORIZATION, auth(customer.token())))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'HIDDEN-01')]").exists());
        mvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk());

        String freshSku = "ADM" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        JsonNode created = body(mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(freshSku.toLowerCase(), "Admin Item", 9_000, 4, "PUBLISHED", "  ", "data:image/jpeg;base64,aaaa")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(freshSku))
                .andExpect(jsonPath("$.categoryName").doesNotExist())
                .andExpect(jsonPath("$.imageUrl", startsWith("data:image/")))
                .andReturn());

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(freshSku, "Dup", 1, 1, "DRAFT", null, null)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/admin/products/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(freshSku, "Missing", 1, 1, "DRAFT", null, null)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/products/" + created.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("IGNORED", "Renamed Item", 9_500, 4, "PUBLISHED", "Wearables", "/products/admin.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(freshSku))
                .andExpect(jsonPath("$.name").value("Renamed Item"))
                .andExpect(jsonPath("$.categoryName").value("Wearables"));
        mvc.perform(patch("/api/v1/admin/products/" + created.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(freshSku, "Renamed Item", 9_500, 4, "PUBLISHED", "Wearables", "https://evil.example/a.jpg")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/products/" + created.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(freshSku, "Renamed Item", 9_500, 4, "PUBLISHED", "Wearables", "x".repeat(400_001))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/products/" + UUID.randomUUID() + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockQty":1}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"BAD","name":"","priceRwf":1,"stockQty":1,"status":"DRAFT"}
                                """))
                .andExpect(status().isBadRequest());

        assertThrows(rw.ac.rca.cloudsphere.common.exception.ApiException.class, () -> adminCatalog.create(
                new UpsertProductRequest("NEGSKU", "Negative", null, 10, -1, ProductStatus.PUBLISHED, null, null)));

        JsonNode alpha = body(mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Alpha Shelf "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Shelf"))
                .andReturn());
        JsonNode beta = body(mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Beta Shelf"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"alpha shelf"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/admin/categories/" + beta.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Beta Shelf"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/categories/" + beta.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alpha Shelf"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/admin/categories/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Missing Shelf"}
                                """))
                .andExpect(status().isNotFound());

        String shipSku = "SHIP" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String shipProduct = createProduct(shipSku, "Ship Item", 3_000, 5, "PUBLISHED", "Cables", null);
        addItem(customer.token(), null, shipProduct, 1);
        JsonNode paid = checkout(customer.token(), "MTN_MOMO", "SUCCESS", null, "ship-" + UUID.randomUUID());
        String paidId = paid.get("id").asText();

        mvc.perform(post("/api/v1/admin/orders/" + paidId + "/deliver").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/orders/" + UUID.randomUUID() + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"RW1"}
                                """))
                .andExpect(status().isNotFound());
        assertThrows(rw.ac.rca.cloudsphere.common.exception.ApiException.class,
                () -> orderService.ship(UUID.randomUUID(), " "));
        assertThrows(rw.ac.rca.cloudsphere.common.exception.ApiException.class,
                () -> orderService.ship(UUID.randomUUID(), null));

        mvc.perform(post("/api/v1/admin/orders/" + paidId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"  RW-100  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipment.trackingNo").value("RW-100"));
        mvc.perform(post("/api/v1/admin/orders/" + paidId + "/deliver").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipment.state").value("DELIVERED"));
        mvc.perform(patch("/api/v1/admin/orders/" + paidId)
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PLACED"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/orders/" + UUID.randomUUID() + "/deliver").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/orders/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isNotFound());

        addItem(customer.token(), null, shipProduct, 1);
        JsonNode placed = checkout(customer.token(), "CARD", "DECLINE", null, null);
        mvc.perform(patch("/api/v1/admin/orders/" + placed.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        addItem(customer.token(), null, shipProduct, 1);
        JsonNode toShip = checkout(customer.token(), "CARD", "SUCCESS", null, null);
        mvc.perform(patch("/api/v1/admin/orders/" + toShip.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/" + toShip.get("id").asText() + "/deliver").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment").doesNotExist())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        dataInitializer.run(applicationArguments);
    }

    private JsonNode checkout(String token, String method, String stubMode, String stubHeader, String idempotencyKey)
            throws Exception {
        String stubField = stubMode == null ? "" : ",\"stubMode\":\"%s\"".formatted(stubMode);
        MockHttpServletRequestBuilder req = post("/api/v1/checkout")
                .header(HttpHeaders.AUTHORIZATION, auth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentMethod":"%s"%s}
                        """.formatted(method, stubField));
        if (stubHeader != null) {
            req.header("X-Payment-Stub-Mode", stubHeader);
        }
        if (idempotencyKey != null) {
            req.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void addItem(String bearer, String guest, String productId, int qty) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productId":"%s","qty":%d}
                        """.formatted(productId, qty));
        if (bearer != null) {
            req.header(HttpHeaders.AUTHORIZATION, auth(bearer));
        }
        if (guest != null) {
            req.header("X-Cart-Token", guest);
        }
        var performed = mvc.perform(req).andExpect(status().isOk());
        if (guest != null) {
            performed.andExpect(header().string("X-Cart-Token", guest));
        }
    }

    private String createProduct(String sku, String name, long price, int stock, String status, String category, String image)
            throws Exception {
        JsonNode node = body(mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, auth(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(sku, name, price, stock, status, category, image)))
                .andExpect(status().isCreated())
                .andReturn());
        return node.get("id").asText();
    }

    private String productIdBySku(String sku) throws Exception {
        JsonNode list = body(mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth(admin())))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode node : list) {
            if (sku.equals(node.get("sku").asText())) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("No product with sku " + sku);
    }

    private String newGuestToken() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Cart-Token"))
                .andReturn();
        return result.getResponse().getHeader("X-Cart-Token");
    }

    private Session register(String email, String password, String phone, String guest) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, password, phone));
        if (guest != null) {
            req.header("X-Cart-Token", guest);
        }
        JsonNode node = body(mvc.perform(req).andExpect(status().isCreated()).andReturn());
        return new Session(node.get("accessToken").asText(), node.get("user").get("id").asText());
    }

    private Session login(String email, String password, String guest) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password));
        if (guest != null) {
            req.header("X-Cart-Token", guest);
        }
        JsonNode node = body(mvc.perform(req).andExpect(status().isOk()).andReturn());
        return new Session(node.get("accessToken").asText(), node.get("user").get("id").asText());
    }

    private String admin() throws Exception {
        if (adminToken == null) {
            adminToken = login("ops@cloudsphere.rw", DataInitializer.DEMO_PASSWORD, null).token();
        }
        return adminToken;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String auth(String token) {
        return "Bearer " + token;
    }

    private static String email(String label) {
        return label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@cloudsphere.rw";
    }

    private static String registerBody(String email, String password, String phone) {
        return """
                {"email":"%s","password":"%s","phone":"%s"}
                """.formatted(email, password, phone);
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String productBody(String sku, String name, long price, int stock, String status, String category, String image) {
        String categoryJson = category == null ? "null" : "\"" + category + "\"";
        String imageJson = image == null ? "null" : "\"" + image + "\"";
        return """
                {"sku":"%s","name":"%s","description":"coverage","priceRwf":%d,"stockQty":%d,"status":"%s","category":%s,"imageUrl":%s}
                """.formatted(sku, name, price, stock, status, categoryJson, imageJson);
    }

    private void saveCode(String code, int percent, int minQty, boolean active, Instant from, Instant until) {
        if (discountCodes.findByCodeIgnoreCase(code).isPresent()) {
            return;
        }
        DiscountCode row = new DiscountCode();
        row.setCode(code);
        row.setPercentOff(percent);
        row.setMinQty(minQty);
        row.setActive(active);
        row.setValidFrom(from);
        row.setValidUntil(until);
        discountCodes.save(row);
    }

    private record Session(String token, String userId) {}
}
