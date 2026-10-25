package rw.ac.rca.cloudsphere.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JPA slice for {@link ProductRepository}. PostgreSQL runs the Flyway script.
 * Each method rolls back, so rows do not leak into the next method.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class ProductRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("cloudsphere")
            .withUsername("cloudsphere")
            .withPassword("cloudsphere");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ProductRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void save_newProduct_canBeReadBackById() {
        // Arrange
        Product product = product("SKU-1", "Cable", ProductStatus.PUBLISHED, 4);

        // Act
        Product saved = repository.saveAndFlush(product);
        entityManager.clear();
        Product found = repository.findById(saved.getId()).orElseThrow();

        // Assert
        assertEquals("SKU-1", found.getSku());
        assertEquals("Cable", found.getName());
        assertEquals(4, found.getStockQty());
        assertEquals(ProductStatus.PUBLISHED, found.getStatus());
        assertTrue(repository.existsById(saved.getId()));
        assertEquals(1, repository.count());
    }

    @Test
    void findById_missingId_returnsEmpty() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
        assertFalse(repository.existsById(UUID.randomUUID()));
    }

    @Test
    void save_duplicateSku_rejectedByTheDatabase() {
        repository.saveAndFlush(product("SKU-1", "Cable", ProductStatus.DRAFT, 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(product("SKU-1", "Other", ProductStatus.DRAFT, 1)));
    }

    @Test
    void findBySkuIgnoreCase_differentCase_returnsTheRow() {
        Product saved = persist("sku-1", "Cable", ProductStatus.DRAFT, 1);

        Product found = repository.findBySkuIgnoreCase("SKU-1").orElseThrow();

        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void findBySkuIgnoreCase_unknownSku_returnsEmpty() {
        persist("SKU-1", "Cable", ProductStatus.DRAFT, 1);

        assertTrue(repository.findBySkuIgnoreCase("missing").isEmpty());
    }

    @Test
    void findBySkuIgnoreCase_twoCaseVariants_throws() {
        // The unique constraint is case-sensitive, so both rows fit.
        persist("sku-1", "Lower", ProductStatus.DRAFT, 1);
        persist("SKU-1", "Upper", ProductStatus.DRAFT, 1);

        assertThrows(IncorrectResultSizeDataAccessException.class,
                () -> repository.findBySkuIgnoreCase("sku-1"));
    }

    @Test
    void existsBySkuIgnoreCase_knownSku_returnsTrue() {
        persist("sku-1", "Cable", ProductStatus.DRAFT, 1);

        assertTrue(repository.existsBySkuIgnoreCase("SKU-1"));
    }

    @Test
    void existsBySkuIgnoreCase_unknownSku_returnsFalse() {
        persist("SKU-1", "Cable", ProductStatus.DRAFT, 1);

        assertFalse(repository.existsBySkuIgnoreCase("other"));
    }

    @Test
    void findByStatus_published_excludesDrafts() {
        persist("PUB-1", "Published cable", ProductStatus.PUBLISHED, 2);
        persist("DRF-1", "Draft cable", ProductStatus.DRAFT, 2);

        Page<Product> page = repository.findByStatus(ProductStatus.PUBLISHED, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("PUB-1", page.getContent().get(0).getSku());
    }

    @Test
    void findByStatus_noneOfThatStatus_returnsEmptyPage() {
        persist("DRF-1", "Draft cable", ProductStatus.DRAFT, 1);

        Page<Product> page = repository.findByStatus(ProductStatus.PUBLISHED, PageRequest.of(0, 10));

        assertEquals(0, page.getTotalElements());
        assertTrue(page.getContent().isEmpty());
    }

    @Test
    void findByStatusAndNameContainingIgnoreCase_fragment_matchesIgnoringCase() {
        persist("PUB-1", "USB Cable", ProductStatus.PUBLISHED, 3);
        persist("PUB-2", "Charger", ProductStatus.PUBLISHED, 3);
        persist("DRF-1", "usb cable draft", ProductStatus.DRAFT, 3);

        Page<Product> page = repository.findByStatusAndNameContainingIgnoreCase(
                ProductStatus.PUBLISHED, "cable", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("PUB-1", page.getContent().get(0).getSku());
    }

    @Test
    void findByStatusAndNameContainingIgnoreCase_noFragment_returnsEmpty() {
        persist("PUB-1", "USB Cable", ProductStatus.PUBLISHED, 1);

        Page<Product> page = repository.findByStatusAndNameContainingIgnoreCase(
                ProductStatus.PUBLISHED, "missing", PageRequest.of(0, 10));

        assertEquals(0, page.getTotalElements());
    }

    @Test
    void findByStatusAndNameContainingIgnoreCase_pageSizeOne_reportsTheFullTotal() {
        persist("PUB-1", "Alpha cable", ProductStatus.PUBLISHED, 1);
        persist("PUB-2", "Beta cable", ProductStatus.PUBLISHED, 1);

        Page<Product> page = repository.findByStatusAndNameContainingIgnoreCase(
                ProductStatus.PUBLISHED, "cable", PageRequest.of(0, 1));

        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void findByIdAndStatus_matchingStatus_returnsTheRow() {
        Product saved = persist("PUB-1", "Cable", ProductStatus.PUBLISHED, 1);

        Product found = repository.findByIdAndStatus(saved.getId(), ProductStatus.PUBLISHED).orElseThrow();

        assertEquals("PUB-1", found.getSku());
    }

    @Test
    void findByIdAndStatus_otherStatus_returnsEmpty() {
        Product saved = persist("PUB-1", "Cable", ProductStatus.PUBLISHED, 1);

        assertTrue(repository.findByIdAndStatus(saved.getId(), ProductStatus.DRAFT).isEmpty());
    }

    @Test
    void findByIdAndStatus_missingId_returnsEmpty() {
        assertTrue(repository.findByIdAndStatus(UUID.randomUUID(), ProductStatus.PUBLISHED).isEmpty());
    }

    @Test
    void decrementStock_enoughOnHand_reducesQuantity() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.PUBLISHED, 5);

        int updated = repository.decrementStock(saved.getId(), 2);
        entityManager.clear();

        assertEquals(1, updated);
        assertEquals(3, repository.findById(saved.getId()).orElseThrow().getStockQty());
    }

    @Test
    void decrementStock_exactQuantity_reachesZero() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.PUBLISHED, 2);

        int updated = repository.decrementStock(saved.getId(), 2);
        entityManager.clear();

        assertEquals(1, updated);
        assertEquals(0, repository.findById(saved.getId()).orElseThrow().getStockQty());
    }

    @Test
    void decrementStock_moreThanOnHand_leavesTheRow() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.PUBLISHED, 2);

        int updated = repository.decrementStock(saved.getId(), 3);
        entityManager.clear();

        assertEquals(0, updated);
        assertEquals(2, repository.findById(saved.getId()).orElseThrow().getStockQty());
    }

    @Test
    void decrementStock_missingId_updatesNothing() {
        int updated = repository.decrementStock(UUID.randomUUID(), 1);

        assertEquals(0, updated);
    }

    @Test
    void incrementStock_knownId_addsQuantity() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.PUBLISHED, 1);

        int updated = repository.incrementStock(saved.getId(), 4);
        entityManager.clear();

        assertEquals(1, updated);
        assertEquals(5, repository.findById(saved.getId()).orElseThrow().getStockQty());
    }

    @Test
    void incrementStock_missingId_updatesNothing() {
        int updated = repository.incrementStock(UUID.randomUUID(), 4);

        assertEquals(0, updated);
    }

    @Test
    void incrementStock_belowZero_rejectedByTheCheck() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.PUBLISHED, 1);

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.incrementStock(saved.getId(), -2));
    }

    @Test
    void deleteById_knownId_removesTheRow() {
        Product saved = persist("SKU-1", "Cable", ProductStatus.DRAFT, 1);

        repository.deleteById(saved.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(saved.getId()).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    void deleteById_missingId_leavesOtherRows() {
        persist("SKU-1", "Cable", ProductStatus.DRAFT, 1);

        repository.deleteById(UUID.randomUUID());
        entityManager.flush();

        assertEquals(1, repository.count());
    }

    private Product persist(String sku, String name, ProductStatus status, int stock) {
        Product saved = repository.saveAndFlush(product(sku, name, status, stock));
        entityManager.clear();
        return saved;
    }

    private static Product product(String sku, String name, ProductStatus status, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setPriceRwf(1_000);
        product.setStockQty(stock);
        product.setStatus(status);
        return product;
    }
}
