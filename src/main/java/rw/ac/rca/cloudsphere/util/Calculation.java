package rw.ac.rca.cloudsphere.util;

/**
 * One stored calculation. The id is assigned by the repository, so a new row starts with a null id.
 */
public final class Calculation {

    private final Long id;
    private final int left;
    private final int right;
    private final int result;

    public Calculation(Long id, int left, int right, int result) {
        this.id = id;
        this.left = left;
        this.right = right;
        this.result = result;
    }

    public Long getId() {
        return id;
    }

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    public int getResult() {
        return result;
    }
}
