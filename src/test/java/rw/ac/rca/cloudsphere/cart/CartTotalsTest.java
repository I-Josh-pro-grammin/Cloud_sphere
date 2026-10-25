package rw.ac.rca.cloudsphere.cart;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CartTotalsTest {

    @Test
    void totals_twoLines_sumQuantityAndSubtotal() {
        Cart cart = new Cart();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cart.addItem(new CartItem(first, 2, 1_000));
        cart.addItem(new CartItem(second, 1, 500));
        assertEquals(3, cart.totalQuantity());
        assertEquals(2_500, cart.subtotalRwf());
        assertEquals(first, cart.itemFor(first).orElseThrow().getProductId());
        assertTrue(cart.itemFor(UUID.randomUUID()).isEmpty());
        assertEquals(cart, cart.getItems().get(0).getCart());
    }
}
