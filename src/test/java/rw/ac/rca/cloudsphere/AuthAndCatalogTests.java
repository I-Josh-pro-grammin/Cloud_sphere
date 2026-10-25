package rw.ac.rca.cloudsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import rw.ac.rca.cloudsphere.config.DataInitializer;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class AuthAndCatalogTests {

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
        registry.add("cloudsphere.jwt.secret", () -> "cloudsphere-test-secret-key-must-be-32b!!");
    }

    @Autowired
    MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void health_anyCaller_hidesVersion() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void login_validPassword_returnsToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@cloudsphere.rw","password":"%s"}
                                """.formatted(DataInitializer.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("demo@cloudsphere.rw"));
    }

    @Test
    void login_wrongPassword_returnsGenericDetail() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@cloudsphere.rw","password":"WrongPass1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("email or password is incorrect"));
    }

    @Test
    void products_publishedSku_isVisible() throws Exception {
        MvcResult list = mvc.perform(get("/api/v1/products?q=USB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("USB-C-01"))
                .andReturn();
        JsonNode node = mapper.readTree(list.getResponse().getContentAsString());
        String id = node.get("content").get(0).get("id").asText();
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());
    }

    @Test
    void cart_guest_receivesToken() throws Exception {
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cart-Token", not("")));
    }

    @Test
    void checkout_anonymous_returnsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"MTN_MOMO"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
