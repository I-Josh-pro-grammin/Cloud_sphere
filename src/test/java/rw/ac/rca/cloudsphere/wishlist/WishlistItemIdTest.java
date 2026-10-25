package rw.ac.rca.cloudsphere.wishlist;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("unit")
class WishlistItemIdTest {

    @Test
    void equals_sameUserAndProduct_matches() {
        UUID user = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        WishlistItemId left = new WishlistItemId(user, product);
        WishlistItemId right = new WishlistItemId(user, product);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        right.setProductId(UUID.randomUUID());
        assertNotEquals(left, right);
    }
}
