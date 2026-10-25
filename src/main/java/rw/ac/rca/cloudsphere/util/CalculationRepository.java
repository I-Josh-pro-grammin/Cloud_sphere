package rw.ac.rca.cloudsphere.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores calculations in memory. A new row has a null id and receives the next id.
 * Saving again with that id replaces the row, so the map does not grow.
 */
public class CalculationRepository {

    private final Map<Long, Calculation> items = new LinkedHashMap<>();
    private long nextId = 1;

    public Calculation save(Calculation calculation) {
        long id = calculation.getId() == null ? nextId++ : calculation.getId();
        if (id >= nextId) {
            nextId = id + 1;
        }
        Calculation stored = new Calculation(id, calculation.getLeft(), calculation.getRight(), calculation.getResult());
        items.put(id, stored);
        return stored;
    }

    public List<Calculation> findAll() {
        return List.copyOf(items.values());
    }

    public Optional<Calculation> findById(Long id) {
        return Optional.ofNullable(items.get(id));
    }

    public void deleteById(Long id) {
        items.remove(id);
    }
}
