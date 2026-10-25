package rw.ac.rca.cloudsphere.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import rw.ac.rca.cloudsphere.common.exception.ApiException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
