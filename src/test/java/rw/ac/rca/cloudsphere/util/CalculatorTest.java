package rw.ac.rca.cloudsphere.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * First unit tests in the course. Each method is one sentence:
 * subject, condition, outcome. JUnit builds a new instance of this class
 * for every method, so the calculator field is not shared across tests.
 */
@Tag("unit")
class CalculatorTest {

    private final Calculator calculator = new Calculator();

    @Test
    void add_twoAndThree_returnsFive() {
        // Arrange: calculator field

        // Act
        int sum = calculator.add(2, 3);

        // Assert
        assertEquals(5, sum);
    }

    @Test
    void add_zero_returnsTheOtherNumber() {
        int withRightZero = calculator.add(7, 0);
        int withLeftZero = calculator.add(0, 7);

        assertEquals(7, withRightZero);
        assertEquals(7, withLeftZero);
    }

    @Test
    void add_negatives_returnsTheArithmeticSum() {
        assertEquals(-5, calculator.add(-2, -3));
        assertEquals(1, calculator.add(-2, 3));
    }

    @Test
    void subtract_fiveMinusThree_returnsTwo() {
        assertEquals(2, calculator.subtract(5, 3));
    }

    @Test
    void subtract_smallerFromLarger_returnsNegative() {
        assertEquals(-2, calculator.subtract(3, 5));
    }

    @Test
    void multiply_fourByThree_returnsTwelve() {
        assertEquals(12, calculator.multiply(4, 3));
    }

    @Test
    void multiply_byZero_returnsZero() {
        assertEquals(0, calculator.multiply(9, 0));
    }

    @Test
    void multiply_twoNegatives_returnsPositive() {
        assertEquals(6, calculator.multiply(-2, -3));
    }

    @Test
    void divide_tenByTwo_returnsFive() {
        assertEquals(5, calculator.divide(10, 2));
    }

    @Test
    void divide_fiveByTwo_returnsTwo() {
        // Integer division truncates toward zero. 5 / 2 is 2, not 3.
        assertEquals(2, calculator.divide(5, 2));
    }

    @Test
    void divide_byZero_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.divide(10, 0));

        assertEquals("divisor must not be zero", ex.getMessage());
    }
}
