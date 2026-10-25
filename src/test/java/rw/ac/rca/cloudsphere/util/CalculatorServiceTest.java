package rw.ac.rca.cloudsphere.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito lesson. {@code @Mock} stands in for the repository and the calculator.
 * {@code when} scripts them before the call. {@code verify} checks they were called.
 * Stubbing {@code add} to return 5 does not prove addition. {@link CalculatorTest} does.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CalculatorServiceTest {

    @Mock
    CalculationRepository repository;

    @Mock
    Calculator calculator;

    @InjectMocks
    CalculatorServiceImpl service;

    @Test
    void recordAdd_twoAndThree_savesFive() {
        // Arrange
        when(calculator.add(2, 3)).thenReturn(5);
        when(repository.save(any(Calculation.class)))
               .thenReturn(new Calculation(1L, 2, 3, 5));

        // Act
        Calculation created = service.recordAdd(2, 3);

        // Assert
        assertEquals(1L, created.getId());
        assertEquals(5, created.getResult());
        verify(repository, times(1)).save(any(Calculation.class));
        verify(calculator, times(1)).add(2, 3);
    }

    @Test
    void list_twoRows_returnsBoth() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(
                new Calculation(1L, 2, 3, 5),
                new Calculation(2L, 1, 1, 2)));

        // Act
        List<Calculation> rows = service.list();

        // Assert
        assertEquals(2, rows.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    void find_knownId_returnsTheRow() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.of(new Calculation(1L, 2, 3, 5)));

        // Act
        Calculation found = service.find(1L);

        // Assert
        assertEquals(1L, found.getId());
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void find_missingId_returnsNull() {
        // Arrange
        when(repository.findById(9L)).thenReturn(Optional.empty());

        // Act
        Calculation found = service.find(9L);

        // Assert
        assertNull(found);
        verify(repository, times(1)).findById(9L);
    }

    @Test
    void delete_knownId_deletesOnce() {
        // Arrange
        doNothing().when(repository).deleteById(1L);

        // Act
        service.delete(1L);

        // Assert
        verify(repository, times(1)).deleteById(1L);
    }
}
