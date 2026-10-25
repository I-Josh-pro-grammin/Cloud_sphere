package rw.ac.rca.cloudsphere.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ProductFlagsTest {

    @Test
    void flags_statusAndStock_reflectPublishedAndInStock() {
        Product product = new Product();
        product.setStatus(ProductStatus.DRAFT);
        product.setStockQty(0);
        assertFalse(product.isPublished());
        assertFalse(product.isInStock());

        product.setStatus(ProductStatus.PUBLISHED);
        product.setStockQty(2);
        assertTrue(product.isPublished());
        assertTrue(product.isInStock());
    }

    @Test
    void publicView_missingCategory_omitsCategory() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku("SKU-1");
        product.setName("Cable");
        product.setPriceRwf(100);
        product.setStockQty(0);
        product.setStatus(ProductStatus.PUBLISHED);
        ProductResponse view = ProductResponse.publicView(product);
        assertEquals("OUT_OF_STOCK", view.stockBadge());
        assertNull(view.categoryId());
        assertNull(view.categoryName());
    }
}
