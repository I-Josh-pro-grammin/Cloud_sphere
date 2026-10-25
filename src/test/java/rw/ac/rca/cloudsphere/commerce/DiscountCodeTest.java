package rw.ac.rca.cloudsphere.commerce;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DiscountCodeTest {

    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void isUsable_activeAtMinQty_returnsTrue() {
        DiscountCode code = code(10, 1, true, null, null);
        assertTrue(code.isUsable(now, 1));
    }

    @Test
    void isUsable_qtyBelowMinimum_returnsFalse() {
        DiscountCode code = code(10, 1, true, null, null);
        assertFalse(code.isUsable(now, 0));
    }

    @Test
    void isUsable_inactive_returnsFalse() {
        assertFalse(code(10, 1, false, null, null).isUsable(now, 5));
    }

    @Test
    void isUsable_beforeValidFrom_returnsFalse() {
        assertFalse(code(10, 1, true, now.plusSeconds(1), null).isUsable(now, 1));
    }

    @Test
    void isUsable_atValidFrom_returnsTrue() {
        assertTrue(code(10, 1, true, now, null).isUsable(now, 1));
    }

    @Test
    void isUsable_atValidUntil_returnsTrue() {
        assertTrue(code(10, 1, true, null, now).isUsable(now, 1));
    }

    @Test
    void isUsable_afterValidUntil_returnsFalse() {
        assertFalse(code(10, 1, true, null, now.minusSeconds(1)).isUsable(now, 1));
    }

    private static DiscountCode code(int percent, int minQty, boolean active, Instant from, Instant until) {
        DiscountCode code = new DiscountCode();
        code.setCode("SAVE2026");
        code.setPercentOff(percent);
        code.setMinQty(minQty);
        code.setActive(active);
        code.setValidFrom(from);
        code.setValidUntil(until);
        return code;
    }
}
