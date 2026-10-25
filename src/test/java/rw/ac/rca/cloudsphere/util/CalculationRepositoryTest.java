package rw.ac.rca.cloudsphere.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The repository keeps rows in a map. {@code recordAdd} grows that map.
 * {@code delete} shrinks it. These tests use the real store, not a mock.
 */
@Tag("unit")
class CalculationRepositoryTest {

    private final CalculationRepository repository = new CalculationRepository();
    private final CalculatorService service = new CalculatorServiceImpl(repository, new Calculator());

    @Test
    void recordAdd_twoCalculations_growsTheMap() {
        assertEquals(0, service.list().size());

        Calculation first = service.recordAdd(2, 3);
        assertEquals(1, service.list().size());
        assertEquals(5, first.getResult());

        service.recordAdd(1, 1);
        assertEquals(2, service.list().size());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void delete_knownId_shrinksTheMap() {
        Calculation first = service.recordAdd(2, 3);
        service.recordAdd(4, 1);
        assertEquals(2, service.list().size());

        service.delete(first.getId());

        assertEquals(1, service.list().size());
        assertNull(service.find(first.getId()));
    }

    @Test
    void save_sameId_replacesTheRowWithoutGrowing() {
        Calculation created = repository.save(new Calculation(null, 2, 3, 5));
        repository.save(new Calculation(created.getId(), 2, 3, 9));

        assertEquals(1, repository.findAll().size());
        assertEquals(9, repository.findById(created.getId()).orElseThrow().getResult());
    }
}
