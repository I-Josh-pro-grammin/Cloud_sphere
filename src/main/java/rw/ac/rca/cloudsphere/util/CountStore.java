package rw.ac.rca.cloudsphere.util;

/**
 * Where a word count is stored. A service test mocks this.
 * The mock does not prove a database.
 */
public interface CountStore {

    void save(String text, int count);
}
