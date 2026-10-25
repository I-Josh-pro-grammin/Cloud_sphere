package rw.ac.rca.cloudsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import rw.ac.rca.cloudsphere.catalog.CategoryRepository;
import rw.ac.rca.cloudsphere.catalog.Product;
import rw.ac.rca.cloudsphere.catalog.ProductRepository;
import rw.ac.rca.cloudsphere.config.DataInitializer;
import rw.ac.rca.cloudsphere.order.OrderRepository;
import rw.ac.rca.cloudsphere.order.OrderStatus;
import rw.ac.rca.cloudsphere.order.ShopOrder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminController with the real AdminCatalogService and OrderService.
 * MockMvc does not open a port. PostgreSQL and Redis do start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class AdminControllerTests {

    private static final String SECRET = "cloudsphere-test-secret-key-must-be-32b!!";

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
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired OrderRepository orders;

    private final ObjectMapper mapper = new ObjectMapper();
    private String adminToken;

    @Test
    void products_noToken_returnsUnauthorized() throws Exception {
        // Arrange: no Authorization header

        // Act
        mvc.perform(get("/api/v1/admin/products"))

                // Assert
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void products_customerToken_returnsForbidden() throws Exception {
        // Arrange
        String customer = register();

        // Act
        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, bearer(customer)))

                // Assert
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not allowed to perform this action."));
    }

    @Test
    void create_newSku_persistsTheProduct() throws Exception {
        // Arrange
        String sku = sku();

        // Act
        JsonNode created = body(mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(sku.toLowerCase(), "Lecture Cable", 4_000, 6, "PUBLISHED", "Cables")))
                .andExpect(status().isCreated())
                .andReturn());

        // Assert
        assertEquals(sku, created.get("sku").asText());
        assertEquals("Cables", created.get("categoryName").asText());
        Product row = products.findBySkuIgnoreCase(sku).orElseThrow();
        assertEquals("Lecture Cable", row.getName());
        assertEquals(6, row.getStockQty());
        assertEquals(4_000, row.getPriceRwf());
    }

    @Test
    void create_sameSkuAgain_returnsConflict() throws Exception {
        // Arrange
        String sku = sku();
        createProduct(sku, "First", 1_000, 2);

        // Act
        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(sku.toLowerCase(), "Second", 1_000, 2, "DRAFT", null)))

                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("SKU already exists."));
    }

    @Test
    void update_existingProduct_keepsSkuAndRenames() throws Exception {
        // Arrange
        String sku = sku();
        String id = createProduct(sku, "Old Name", 2_000, 3);

        // Act
        mvc.perform(patch("/api/v1/admin/products/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("IGNORED", "New Name", 2_500, 3, "PUBLISHED", "Cables")))

                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.priceRwf").value(2_500));
        assertEquals("New Name", products.findBySkuIgnoreCase(sku).orElseThrow().getName());
    }

    @Test
    void update_missingId_returnsNotFound() throws Exception {
        // Arrange
        String missing = UUID.randomUUID().toString();

        // Act
        mvc.perform(patch("/api/v1/admin/products/" + missing)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("MISSING", "Missing", 1, 1, "DRAFT", null)))

                // Assert
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found"));
    }

    @Test
    void updateStock_knownProduct_persistsQuantity() throws Exception {
        // Arrange
        String sku = sku();
        String id = createProduct(sku, "Stocked", 1_500, 4);

        // Act
        mvc.perform(patch("/api/v1/admin/products/" + id + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockQty":12}
                                """))

                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(12));
        assertEquals(12, products.findBySkuIgnoreCase(sku).orElseThrow().getStockQty());
    }

    @Test
    void createCategory_trimmedName_persistsCategory() throws Exception {
        // Arrange
        String name = "Shelf " + UUID.randomUUID().toString().substring(0, 8);

        // Act
        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  %s  "}
                                """.formatted(name)))

                // Assert
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));
        assertEquals(name, categories.findByNameIgnoreCase(name).orElseThrow().getName());
    }

    @Test
    void renameCategory_takenName_returnsConflict() throws Exception {
        // Arrange
        String taken = "Taken " + UUID.randomUUID().toString().substring(0, 8);
        String other = "Other " + UUID.randomUUID().toString().substring(0, 8);
        createCategory(taken);
        String otherId = createCategory(other);

        // Act
        mvc.perform(patch("/api/v1/admin/categories/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(taken)))

                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A category with that name already exists."));
        assertEquals(other, categories.findByNameIgnoreCase(other).orElseThrow().getName());
    }

    @Test
    void ship_paidOrder_persistsTracking() throws Exception {
        // Arrange
        String orderId = paidOrder();

        // Act
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"  RW-100  "}
                                """))

                // Assert
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipment.trackingNo").value("RW-100"));
        ShopOrder row = orders.findById(UUID.fromString(orderId)).orElseThrow();
        assertEquals(OrderStatus.SHIPPED, row.getStatus());
        assertEquals("RW-100", row.getShipment().getTrackingNo());
    }

    @Test
    void deliver_paidOrder_returnsConflict() throws Exception {
        // Arrange
        String orderId = paidOrder();

        // Act
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/deliver")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin())))

                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Illegal order transition: PAID → DELIVERED"));
        assertEquals(OrderStatus.PAID, orders.findById(UUID.fromString(orderId)).orElseThrow().getStatus());
    }

    @Test
    void changeStatus_deliveredToPlaced_returnsConflict() throws Exception {
        // Arrange
        String orderId = paidOrder();
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingNo":"RW-200"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/deliver")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin())))
                .andExpect(status().isOk());

        // Act
        mvc.perform(patch("/api/v1/admin/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PLACED"}
                                """))

                // Assert
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Illegal order transition: DELIVERED → PLACED"));
        assertEquals(OrderStatus.DELIVERED, orders.findById(UUID.fromString(orderId)).orElseThrow().getStatus());
    }

    private String paidOrder() throws Exception {
        String productId = createProduct(sku(), "Ship Item", 3_000, 5);
        String customer = register();
        mvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","qty":1}
                                """.formatted(productId)))
                .andExpect(status().isOk());
        JsonNode paid = body(mvc.perform(post("/api/v1/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CARD","stubMode":"SUCCESS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        assertEquals("PAID", paid.get("status").asText());
        return paid.get("id").asText();
    }

    private String createProduct(String sku, String name, long price, int stock) throws Exception {
        JsonNode created = body(mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(sku, name, price, stock, "PUBLISHED", "Cables")))
                .andExpect(status().isCreated())
                .andReturn());
        return created.get("id").asText();
    }

    private String createCategory(String name) throws Exception {
        JsonNode created = body(mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn());
        return created.get("id").asText();
    }

    private String admin() throws Exception {
        if (adminToken == null) {
            adminToken = login("ops@cloudsphere.rw", DataInitializer.DEMO_PASSWORD);
        }
        return adminToken;
    }

    private String register() throws Exception {
        String email = "buyer-" + UUID.randomUUID().toString().substring(0, 8) + "@cloudsphere.rw";
        int mobile = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        JsonNode node = body(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"CloudSphere1","phone":"07%d"}
                                """.formatted(email, mobile)))
                .andExpect(status().isCreated())
                .andReturn());
        return node.get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        JsonNode node = body(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn());
        return node.get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sku() {
        return "ADM" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String productBody(String sku, String name, long price, int stock, String status, String category) {
        String categoryJson = category == null ? "null" : "\"" + category + "\"";
        return """
                {"sku":"%s","name":"%s","description":"lecture","priceRwf":%d,"stockQty":%d,"status":"%s","category":%s}
                """.formatted(sku, name, price, stock, status, categoryJson);
    }
}
