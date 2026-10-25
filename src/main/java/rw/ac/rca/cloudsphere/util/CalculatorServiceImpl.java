package rw.ac.rca.cloudsphere.util;

import java.util.List;

public class CalculatorServiceImpl implements CalculatorService {

    private final CalculationRepository repository;
    private final Calculator calculator;

    public CalculatorServiceImpl(CalculationRepository repository, Calculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public Calculation recordAdd(int left, int right) {
        int result = calculator.add(left, right);
        return repository.save(new Calculation(null, left, right, result));
    }

    @Override
    public List<Calculation> list() {
        return repository.findAll();
    }

    @Override
    public Calculation find(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
