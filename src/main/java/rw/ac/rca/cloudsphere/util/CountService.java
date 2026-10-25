package rw.ac.rca.cloudsphere.util;

/**
 * Same shape as {@link CalculatorServiceImpl}: one collaborator that computes,
 * one collaborator that stores. A count of zero is not stored.
 *
 * Student exercise: write {@code CountServiceTest} with Mockito, the same way as
 * {@code CalculatorServiceTest}. Mock {@link WordCounter} and {@link CountStore}.
 * Two words returns 2 and saves once. A blank string returns 0 and does not save.
 */
public class CountService {

    private final WordCounter counter;
    private final CountStore store;

    public CountService(WordCounter counter, CountStore store) {
        this.counter = counter;
        this.store = store;
    }

    public int countAndStore(String text) {
        int n = counter.count(text);
        if (n == 0) {
            return 0;
        }
        store.save(text, n);
        return n;
    }
}
