package rw.ac.rca.cloudsphere.commerce;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rw.ac.rca.cloudsphere.common.exception.ApiException;
import rw.ac.rca.cloudsphere.identity.LoyaltyTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PricingTest {

    @Test
    void calculateTotal_fiveItems_appliesTenPercent() {
        assertEquals(450, Pricing.calculateTotal(5, 100));
    }

    @Test
    void calculateTotal_fourItems_chargesFullPrice() {
        assertEquals(400, Pricing.calculateTotal(4, 100));
    }

    @Test
    void calculateTotal_negativeQuantity_throws() {
        assertThrows(IllegalArgumentException.class, () -> Pricing.calculateTotal(-1, 100));
    }

    @Test
    void discountRwf_codeAndQuantity_keepsTheLarger() {
        long subtotal = 5 * 15_000;
        long discount = Pricing.discountRwf(subtotal, 5, 10);
        assertEquals(subtotal * 10 / 100, discount);
    }

    @Test
    void shippingRwf_goldBelowThreshold_isFree() {
        assertEquals(0, Pricing.shippingRwf(30_000, LoyaltyTier.GOLD));
    }

    @Test
    void shippingRwf_standardBelowThreshold_costs2000() {
        assertEquals(2_000, Pricing.shippingRwf(30_000, LoyaltyTier.STANDARD));
    }

    @Test
    void shippingRwf_standardAtThreshold_isFree() {
        assertEquals(0, Pricing.shippingRwf(50_000, LoyaltyTier.STANDARD));
    }

    @Test
    void assertCodeFormat_fiveToTenLettersOrDigits_isAccepted() {
        Pricing.assertCodeFormat("SAVE2");
        Pricing.assertCodeFormat("SAVE2026");
    }

    @Test
    void assertCodeFormat_wrongShape_throws() {
        assertThrows(ApiException.class, () -> Pricing.assertCodeFormat(null));
        assertThrows(ApiException.class, () -> Pricing.assertCodeFormat("   "));
        assertThrows(ApiException.class, () -> Pricing.assertCodeFormat("AB12"));
        assertThrows(ApiException.class, () -> Pricing.assertCodeFormat("SAVE2026SUMMER"));
        assertThrows(ApiException.class, () -> Pricing.assertCodeFormat("SAVE-25!"));
    }

    @Test
    void quantityDiscountPercent_fourItems_isZero() {
        assertEquals(0, Pricing.quantityDiscountPercent(4));
    }

    @Test
    void quantityDiscountPercent_fiveItems_isTen() {
        assertEquals(10, Pricing.quantityDiscountPercent(5));
    }

    @Test
    void discountRwf_codeAboveQuantityDiscount_keepsTheCode() {
        assertEquals(1_500, Pricing.discountRwf(10_000, 5, 15));
    }

    @Test
    void discountRwf_nullCodePercent_isZero() {
        assertEquals(0, Pricing.discountRwf(10_000, 1, null));
    }

    @Test
    void calculateTotal_negativeUnitPrice_throws() {
        assertThrows(IllegalArgumentException.class, () -> Pricing.calculateTotal(1, -1));
    }

    @Test
    void shippingRwf_standardJustBelowThreshold_costs2000() {
        assertEquals(2_000, Pricing.shippingRwf(49_999, LoyaltyTier.STANDARD));
    }

    @Test
    void shippingRwf_goldJustBelowThreshold_isFree() {
        assertEquals(0, Pricing.shippingRwf(49_999, LoyaltyTier.GOLD));
    }
}
