package rw.ac.rca.cloudsphere.util;

import java.util.List;

/**
 * Records arithmetic and reads it back. The implementation talks to {@link Calculator}
 * and {@link CalculationRepository}. A unit test mocks those two and keeps this contract real.
 */
public interface CalculatorService {

    Calculation recordAdd(int left, int right);

    List<Calculation> list();

    Calculation find(Long id);

    void delete(Long id);
}
