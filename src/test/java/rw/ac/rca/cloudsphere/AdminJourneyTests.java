package rw.ac.rca.cloudsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import rw.ac.rca.cloudsphere.catalog.ProductRepository;
import rw.ac.rca.cloudsphere.config.DataInitializer;
import rw.ac.rca.cloudsphere.order.OrderRepository;
import rw.ac.rca.cloudsphere.order.OrderStatus;
import rw.ac.rca.cloudsphere.order.ShopOrder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One admin journey on a real listen port. TestRestTemplate calls AdminController.
 * AdminCatalogService and OrderService write the rows. The React app does not start.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@Tag("e2e")
class AdminJourneyTests {

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

    @Autowired TestRestTemplate http;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void admin_publishedProductAndPaidOrder_reachesDelivered() throws Exception {
        // Arrange
        String admin = login("ops@cloudsphere.rw", DataInitializer.DEMO_PASSWORD);
        String customer = register();
        String sku = "E2E" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Act: publish, sell one unit, correct the stock, ship, deliver
        JsonNode created = read(exchange(HttpMethod.POST, "/api/v1/admin/products", admin, """
                {"sku":"%s","name":"Journey Handset","description":"lecture","priceRwf":8000,"stockQty":5,"status":"PUBLISHED","category":"Cables"}
                """.formatted(sku), HttpStatus.CREATED));
        String productId = created.get("id").asText();

        exchange(HttpMethod.POST, "/api/v1/cart/items", customer, """
                {"productId":"%s","qty":1}
                """.formatted(productId), HttpStatus.OK);
        JsonNode paid = read(exchange(HttpMethod.POST, "/api/v1/checkout", customer, """
                {"paymentMethod":"MTN_MOMO","stubMode":"SUCCESS"}
                """, HttpStatus.CREATED));
        String orderId = paid.get("id").asText();

        read(exchange(HttpMethod.PATCH, "/api/v1/admin/products/" + productId + "/stock", admin, """
                {"stockQty":9}
                """, HttpStatus.OK));
        read(exchange(HttpMethod.POST, "/api/v1/admin/orders/" + orderId + "/ship", admin, """
                {"trackingNo":"  CS-TRACK-001  "}
                """, HttpStatus.OK));
        JsonNode delivered = read(exchange(HttpMethod.POST, "/api/v1/admin/orders/" + orderId + "/deliver", admin,
                null, HttpStatus.OK));

        // Assert: the screen would show the product, the tracking number, and Delivered
        JsonNode catalog = read(exchange(HttpMethod.GET, "/api/v1/admin/products", admin, null, HttpStatus.OK));
        boolean listed = false;
        for (JsonNode product : catalog) {
            if (sku.equals(product.get("sku").asText())) {
                listed = true;
                assertEquals(9, product.get("stockQty").asInt());
            }
        }
        assertTrue(listed);
        assertEquals("DELIVERED", delivered.get("status").asText());
        assertEquals("CS-TRACK-001", delivered.get("shipment").get("trackingNo").asText());

        assertEquals(9, products.findBySkuIgnoreCase(sku).orElseThrow().getStockQty());
        ShopOrder row = orders.findById(UUID.fromString(orderId)).orElseThrow();
        assertEquals(OrderStatus.DELIVERED, row.getStatus());
        assertEquals("CS-TRACK-001", row.getShipment().getTrackingNo());
    }

    private String register() throws Exception {
        String email = "journey-" + UUID.randomUUID().toString().substring(0, 8) + "@cloudsphere.rw";
        int mobile = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        JsonNode node = read(exchange(HttpMethod.POST, "/api/v1/auth/register", null, """
                {"email":"%s","password":"CloudSphere1","phone":"07%d"}
                """.formatted(email, mobile), HttpStatus.CREATED));
        return node.get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        JsonNode node = read(exchange(HttpMethod.POST, "/api/v1/auth/login", null, """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), HttpStatus.OK));
        return node.get("accessToken").asText();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token, String json, HttpStatus expected) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(json, headers), String.class);
        assertEquals(expected, response.getStatusCode());
        return response;
    }

    private JsonNode read(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }
}
