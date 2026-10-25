package rw.ac.rca.cloudsphere.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rw.ac.rca.cloudsphere.common.exception.ApiException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ProductFlagsTest {

    @Test
    void flags_statusAndStock_reflectPublishedAndInStock() {
        Product product = new Product();
        product.setStatus(ProductStatus.DRAFT);
        product.setStockQty(0);
        assertFalse(product.isPublished());
        assertFalse(product.isInStock());

        product.setStatus(ProductStatus.PUBLISHED);
        product.setStockQty(2);
        assertTrue(product.isPublished());
        assertTrue(product.isInStock());
    }

    @Test
    void publicView_missingCategory_omitsCategory() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku("SKU-1");
        product.setName("Cable");
        product.setPriceRwf(100);
        product.setStockQty(0);
        product.setStatus(ProductStatus.PUBLISHED);
        ProductResponse view = ProductResponse.publicView(product);
        assertEquals("OUT_OF_STOCK", view.stockBadge());
        assertNull(view.categoryId());
        assertNull(view.categoryName());
    }

    @ExtendWith(MockitoExtension.class)
    static
    class CatalogServiceTest {

        @Mock
        private ProductRepository productRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @InjectMocks
        private CatalogService catalogService;

        @Nested
        @DisplayName("Tests for search()")
        class SearchTests {

            @Test
            @DisplayName("Should call findByStatus when query is null or blank")
            void search_withBlankQuery_callsFindByStatus() {
                // Arrange
                PageRequest expectedPageable = PageRequest.of(0, 20, Sort.by("name"));
                Page<Product> emptyPage = new PageImpl<>(List.of());

                when(productRepository.findByStatus(eq(ProductStatus.PUBLISHED), any(PageRequest.class)))
                        .thenReturn(emptyPage);

                // Act
                Page<ProductResponse> result = catalogService.search("   ", 0, 20);

                // Assert
                assertNotNull(result);
                // Verify findByStatus was run exactly once with matching params
                verify(productRepository, times(1)).findByStatus(ProductStatus.PUBLISHED, expectedPageable);
                // Verify the alternative branch query method was NEVER run
                verify(productRepository, never()).findByStatusAndNameContainingIgnoreCase(any(), any(), any());
            }

            @Test
            @DisplayName("Should call findByStatusAndNameContainingIgnoreCase and trim query when query is present")
            void search_withQuery_callsFindByStatusAndNameContainingIgnoreCase() {
                // Arrange
                String rawQuery = "  Laptop  ";
                String expectedQuery = "Laptop";
                PageRequest expectedPageable = PageRequest.of(0, 10, Sort.by("name"));
                Page<Product> emptyPage = new PageImpl<>(List.of());

                when(productRepository.findByStatusAndNameContainingIgnoreCase(eq(ProductStatus.PUBLISHED), eq(expectedQuery), any(PageRequest.class)))
                        .thenReturn(emptyPage);

                // Act
                catalogService.search(rawQuery, 0, 10);

                // Assert & Verify
                verify(productRepository, times(1))
                        .findByStatusAndNameContainingIgnoreCase(ProductStatus.PUBLISHED, expectedQuery, expectedPageable);
                verify(productRepository, never()).findByStatus(any(), any());
            }

            @Test
            @DisplayName("Should fall back to default pagination limits if size is negative or zero")
            void search_withInvalidPageSize_boundsToDefaultLimits() {
                // Arrange
                Page<Product> emptyPage = new PageImpl<>(List.of());
                when(productRepository.findByStatus(any(), any())).thenReturn(emptyPage);

                // Act
                catalogService.search("", -5, 0);

                // Assert & Verify using an ArgumentCaptor to capture the generated Pageable framework object
                ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.of(0,20).getClass());
                verify(productRepository).findByStatus(eq(ProductStatus.PUBLISHED), pageableCaptor.capture());

                PageRequest capturedPageable = pageableCaptor.getValue();
                assertEquals(0, capturedPageable.getPageNumber()); // Negative page maps to 0
                assertEquals(20, capturedPageable.getPageSize());  // Size <= 0 maps to 20
            }
        }

        @Nested
        @DisplayName("Tests for getPublished()")
        class GetPublishedTests {

            @Test
            @DisplayName("Should return ProductResponse when published product is found")
            void getPublished_found_returnsProductResponse() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product mockProduct = new Product(); // Assuming constructor or setters exist
                mockProduct.setId(productId);
                mockProduct.setName("Gadget");
                mockProduct.setStatus(ProductStatus.PUBLISHED);

                when(productRepository.findByIdAndStatus(productId, ProductStatus.PUBLISHED))
                        .thenReturn(Optional.of(mockProduct));

                // Act
                ProductResponse response = catalogService.getPublished(productId);

                // Assert
                assertNotNull(response);
                verify(productRepository, times(1)).findByIdAndStatus(productId, ProductStatus.PUBLISHED);
            }

            @Test
            @DisplayName("Should throw ApiException when product does not exist or is not published")
            void getPublished_notFound_throwsApiException() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndStatus(productId, ProductStatus.PUBLISHED))
                        .thenReturn(Optional.empty());

                // Act & Assert
                ApiException exception = assertThrows(ApiException.class, () -> {
                    catalogService.getPublished(productId);
                });

                // Ensure the error message or type matches your ApiException.notFound structure
                assertTrue(exception.getMessage().contains("Product not found"));
                verify(productRepository, times(1)).findByIdAndStatus(productId, ProductStatus.PUBLISHED);
            }
        }

        @Nested
        @DisplayName("Tests for categories()")
        class CategoriesTests {

            @Test
            @DisplayName("Should return all categories from repository")
            void categories_callsFindAll() {
                // Arrange
                List<Category> mockCategories = List.of(new Category(), new Category());
                when(categoryRepository.findAll()).thenReturn(mockCategories);

                // Act
                List<Category> result = catalogService.categories();

                // Assert
                assertEquals(2, result.size());
                verify(categoryRepository, times(1)).findAll();
            }
        }
    }
}
