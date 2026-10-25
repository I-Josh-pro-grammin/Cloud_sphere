package rw.ac.rca.cloudsphere.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import rw.ac.rca.cloudsphere.catalog.CategoryRepository;
import rw.ac.rca.cloudsphere.catalog.ProductRepository;
import rw.ac.rca.cloudsphere.commerce.DiscountCodeRepository;
import rw.ac.rca.cloudsphere.identity.UserRepository;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class DataInitializerTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock DiscountCodeRepository discountCodes;

    @Test
    void run_seedDisabled_touchesNothing() {
        CloudSphereProperties properties = new CloudSphereProperties(
                new CloudSphereProperties.Jwt("cloudsphere-test-secret-key-must-be-32b!!", "cloudsphere", Duration.ofHours(8)),
                new CloudSphereProperties.Lockout(3, Duration.ofMinutes(30)),
                new CloudSphereProperties.Payment(CloudSphereProperties.StubMode.DECLINE),
                new CloudSphereProperties.Cors(List.of("http://localhost:5173")),
                new CloudSphereProperties.Seed(false)
        );
        DataInitializer initializer = new DataInitializer(properties, users, encoder, categories, products, discountCodes);
        initializer.run(mock(ApplicationArguments.class));
        verifyNoInteractions(users, encoder, categories, products, discountCodes);
    }
}
