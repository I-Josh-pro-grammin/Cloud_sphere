package rw.ac.rca.cloudsphere.admin;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import rw.ac.rca.cloudsphere.catalog.Category;
import rw.ac.rca.cloudsphere.catalog.CategoryRepository;
import rw.ac.rca.cloudsphere.catalog.CategoryResponse;
import rw.ac.rca.cloudsphere.catalog.ProductRepository;
import rw.ac.rca.cloudsphere.common.exception.ApiException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Same lesson as {@code CalculatorServiceTest}, on a real service method.
 * {@code CategoryRepository} is a mock. Nothing here opens PostgreSQL.
 * Stubbing {@code save} does not prove a unique constraint. It proves that
 * {@code createCategory} trims the name, saves a new row, and refuses a duplicate.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminCatalogServiceTest {

    @Mock
    ProductRepository products;

    @Mock
    CategoryRepository categories;

    @InjectMocks
    AdminCatalogService service;

    @Test
    void createCategory_newName_savesTheTrimmedName() {
        // Arrange
        Category stored = new Category("Phones");
        UUID id = stored.getId();
        when(categories.findByNameIgnoreCase("Phones")).thenReturn(Optional.empty());
        when(categories.save(any(Category.class))).thenReturn(stored);

        // Act
        CategoryResponse created = service.createCategory("  Phones  ");

        // Assert
        assertEquals(id, created.id());
        assertEquals("Phones", created.name());
        verify(categories, times(1)).save(argThat(category -> "Phones".equals(category.getName())));
    }

    @Test
    void createCategory_existingName_throwsConflictAndDoesNotSave() {
        // Arrange
        when(categories.findByNameIgnoreCase("Phones"))
                .thenReturn(Optional.of(new Category("Phones")));

        // Act
        ApiException ex = assertThrows(ApiException.class, () -> service.createCategory(" Phones "));

        // Assert
        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("A category with that name already exists.", ex.getMessage());
        verify(categories, never()).save(any());
    }
}
