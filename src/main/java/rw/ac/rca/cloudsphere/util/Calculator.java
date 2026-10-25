package rw.ac.rca.cloudsphere.util;

/**
 * Integer arithmetic used as the first JUnit example.
 * Numbers in, a number out, no Spring. Integer division truncates toward zero.
 */
public final class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return a / b;
    }
}
