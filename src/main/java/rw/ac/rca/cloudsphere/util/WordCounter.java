package rw.ac.rca.cloudsphere.util;

/**
 * How many words are in text. Blank input counts as zero.
 */
public final class WordCounter {

    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
