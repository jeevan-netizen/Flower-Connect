package com.flowerconnect.order;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the V13 migration ({@code carts}, {@code cart_items},
 * {@code orders}, {@code order_items}, {@code order_status_history}) and the
 * {@code service_locations.timezone} ALTER match the entities in
 * {@code com.flowerconnect.cart.domain} and {@code com.flowerconnect.order.domain}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartOrderSchemaIT extends AbstractIntegrationTest {

    private static final List<String> CART_COLUMNS = List.of(
            "id", "user_id", "created_at", "updated_at");

    private static final List<String> CART_ITEM_COLUMNS = List.of(
            "id", "cart_id", "product_id", "vendor_id", "quantity",
            "price_snapshot", "note", "created_at", "updated_at");

    private static final List<String> ORDER_COLUMNS = List.of(
            "id", "user_id", "vendor_profile_id", "client_order_id", "status",
            "payment_method", "payment_status", "subtotal", "delivery_fee",
            "platform_fee", "total", "commission_rate",
            "delivery_address_line1", "delivery_address_line2",
            "delivery_service_location_id", "delivery_latitude", "delivery_longitude",
            "slot_start", "slot_end", "note", "created_at", "updated_at");

    private static final List<String> ORDER_ITEM_COLUMNS = List.of(
            "id", "order_id", "product_id", "vendor_id", "quantity",
            "price_snapshot", "note", "created_at");

    private static final List<String> OSH_COLUMNS = List.of(
            "id", "order_id", "from_status", "to_status", "reason",
            "actor_user_id", "created_at");

    // The exact ENUM list that Order.OrderStatus must mirror (D-20).
    private static final List<String> ORDER_STATUS_VALUES = List.of(
            "PLACED", "PENDING_PAYMENT", "CONFIRMED", "PREPARING",
            "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "REJECTED", "REFUNDED");

    private static final List<String> PAYMENT_STATUS_VALUES = List.of(
            "PENDING", "PAID", "COLLECTED", "FAILED", "REFUND_PENDING", "REFUNDED");

    @Test
    void serviceLocationsShouldHaveTimezoneColumn() {
        String type = columnType("service_locations", "timezone");
        assertNotNull(type, "Missing column service_locations.timezone (decision D-5)");
        assertTrue(type.startsWith("varchar"), "timezone must be varchar, was: " + type);
        assertEquals("Asia/Kolkata", columnDefault("service_locations", "timezone"));
    }

    @Test
    void cartsShouldHaveEveryPlannedColumn() {
        for (String column : CART_COLUMNS) {
            assertNotNull(columnType("carts", column), "Missing column carts." + column);
        }
    }

    @Test
    void cartsShouldHaveOneCartPerUser() {
        assertTrue(indexExists("carts", "uq_carts_user", "user_id"));
        assertTrue(foreignKeyExists("carts", "user_id", "users"));
    }

    @Test
    void cartItemsShouldHaveEveryPlannedColumn() {
        for (String column : CART_ITEM_COLUMNS) {
            assertNotNull(columnType("cart_items", column), "Missing column cart_items." + column);
        }
    }

    @Test
    void cartItemsShouldHavePlannedColumnTypes() {
        assertEquals("decimal(10,2)", columnType("cart_items", "price_snapshot"));
        assertTrue(columnType("cart_items", "quantity").startsWith("int"));
    }

    @Test
    void cartItemsShouldEnforceOneLinePerProduct() {
        assertTrue(indexExists("cart_items", "uq_cart_item_cart_product", "cart_id", "product_id"));
        assertTrue(foreignKeyExists("cart_items", "cart_id", "carts"));
        assertTrue(foreignKeyExists("cart_items", "product_id", "products"));
    }

    @Test
    void cartItemsShouldCascadeDeleteWhenCartIsDeleted() {
        String deleteRule = jdbcTemplate.queryForObject(
                """
                SELECT rc.DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS rc
                WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
                  AND rc.TABLE_NAME = 'cart_items'
                  AND rc.REFERENCED_TABLE_NAME = 'carts'
                """,
                String.class);
        assertNotNull(deleteRule);
        assertEquals("CASCADE", deleteRule);
    }

    @Test
    void ordersShouldHaveEveryPlannedColumn() {
        for (String column : ORDER_COLUMNS) {
            assertNotNull(columnType("orders", column), "Missing column orders." + column);
        }
    }

    @Test
    void ordersShouldHavePlannedColumnTypes() {
        assertEquals("decimal(10,2)", columnType("orders", "subtotal"));
        assertEquals("decimal(10,2)", columnType("orders", "delivery_fee"));
        assertEquals("decimal(10,2)", columnType("orders", "platform_fee"));
        assertEquals("decimal(10,2)", columnType("orders", "total"));
        assertEquals("decimal(5,4)", columnType("orders", "commission_rate"));
        assertEquals("decimal(10,8)", columnType("orders", "delivery_latitude"));
        assertEquals("decimal(11,8)", columnType("orders", "delivery_longitude"));
        assertEquals("varchar(36)", columnType("orders", "client_order_id"));
    }

    @Test
    void ordersShouldHaveIdempotencyKeyConstraint() {
        assertTrue(indexExists("orders", "uq_orders_client_order_id", "user_id", "client_order_id"));
        assertTrue(foreignKeyExists("orders", "user_id", "users"));
        assertTrue(foreignKeyExists("orders", "vendor_profile_id", "vendor_profiles"));
        assertTrue(foreignKeyExists("orders", "delivery_service_location_id", "service_locations"));
    }

    @Test
    void ordersShouldHavePlannedIndexes() {
        assertTrue(indexExists("orders", "idx_orders_user", "user_id"));
        assertTrue(indexExists("orders", "idx_orders_vendor_status", "vendor_profile_id", "status"));
        assertTrue(indexExists("orders", "idx_orders_slot", "vendor_profile_id", "slot_start", "slot_end"));
    }

    @Test
    void ordersStatusShouldDefaultToPlaced() {
        assertEquals("PLACED", columnDefault("orders", "status"));
    }

    @Test
    void ordersPaymentMethodShouldDefaultToCOD() {
        assertEquals("COD", columnDefault("orders", "payment_method"));
    }

    @Test
    void ordersPaymentStatusShouldDefaultToPending() {
        assertEquals("PENDING", columnDefault("orders", "payment_status"));
    }

    @Test
    void orderItemsShouldHaveEveryPlannedColumn() {
        for (String column : ORDER_ITEM_COLUMNS) {
            assertNotNull(columnType("order_items", column), "Missing column order_items." + column);
        }
    }

    @Test
    void orderItemsShouldHavePlannedColumnTypes() {
        assertEquals("decimal(10,2)", columnType("order_items", "price_snapshot"));
        assertTrue(columnType("order_items", "quantity").startsWith("int"));
    }

    @Test
    void orderItemsShouldCascadeDeleteWhenOrderIsDeleted() {
        assertTrue(indexExists("order_items", "idx_order_items_order", "order_id"));
    }

    @Test
    void orderStatusHistoryShouldHaveEveryPlannedColumn() {
        for (String column : OSH_COLUMNS) {
            assertNotNull(columnType("order_status_history", column),
                    "Missing column order_status_history." + column);
        }
    }

    @Test
    void orderStatusHistoryShouldHavePlannedIndexes() {
        assertTrue(indexExists("order_status_history", "idx_osh_order", "order_id", "created_at"));
        assertTrue(foreignKeyExists("order_status_history", "order_id", "orders"));
        assertTrue(foreignKeyExists("order_status_history", "actor_user_id", "users"));
    }

    @Test
    void orderStatusHistoryEnumShouldMatchOrdersStatusExactly() {
        // D-20: from_status and to_status must use the EXACT same MySQL ENUM value list
        // and ordering as orders.status.
        String ordersStatusType = columnType("orders", "status");
        String oshFromStatusType = columnType("order_status_history", "from_status");
        String oshToStatusType = columnType("order_status_history", "to_status");

        assertNotNull(ordersStatusType);
        assertNotNull(oshFromStatusType);
        assertNotNull(oshToStatusType);

        assertTrue(ordersStatusType.startsWith("enum"),
                "orders.status must be an ENUM, was: " + ordersStatusType);
        assertTrue(oshFromStatusType.startsWith("enum"),
                "order_status_history.from_status must be an ENUM, was: " + oshFromStatusType);
        assertTrue(oshToStatusType.startsWith("enum"),
                "order_status_history.to_status must be an ENUM, was: " + oshToStatusType);

        // The raw ENUM column type includes the full value list in order.
        assertTrue(ordersStatusType.contains("'PLACED'"),
                "orders.status must include 'PLACED': " + ordersStatusType);
        assertTrue(oshFromStatusType.contains("'PLACED'"),
                "order_status_history.from_status must include 'PLACED': " + oshFromStatusType);
        assertTrue(oshToStatusType.contains("'PLACED'"),
                "order_status_history.to_status must include 'PLACED': " + oshToStatusType);

        // Every OrderStatus value must appear in all three ENUMs.
        for (String value : ORDER_STATUS_VALUES) {
            assertTrue(ordersStatusType.contains("'" + value + "'"),
                    "orders.status missing enum value: " + value);
            assertTrue(oshFromStatusType.contains("'" + value + "'"),
                    "order_status_history.from_status missing enum value: " + value);
            assertTrue(oshToStatusType.contains("'" + value + "'"),
                    "order_status_history.to_status missing enum value: " + value);
        }
    }

    @Test
    void ordersPaymentMethodShouldAcceptCODAndOnline() {
        String type = columnType("orders", "payment_method");
        assertTrue(type.startsWith("enum"), "payment_method must be an ENUM");
        assertTrue(type.contains("'COD'"), "payment_method must include 'COD'");
        assertTrue(type.contains("'ONLINE'"), "payment_method must include 'ONLINE'");
    }

    @Test
    void ordersPaymentStatusShouldMatchPlannedValues() {
        String type = columnType("orders", "payment_status");
        assertTrue(type.startsWith("enum"), "payment_status must be an ENUM");
        for (String value : PAYMENT_STATUS_VALUES) {
            assertTrue(type.contains("'" + value + "'"),
                    "payment_status missing enum value: " + value);
        }
    }

    private String columnType(String table, String column) {
        List<String> types = jdbcTemplate.queryForList(
                """
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """,
                String.class, table, column);
        return types.isEmpty() ? null : types.get(0);
    }

    private String columnDefault(String table, String column) {
        List<String> defaults = jdbcTemplate.queryForList(
                """
                SELECT COLUMN_DEFAULT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """,
                String.class, table, column);
        return defaults.isEmpty() ? null : defaults.get(0);
    }

    private boolean indexExists(String table, String indexName, String... expectedColumns) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT COLUMN_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                ORDER BY SEQ_IN_INDEX
                """,
                table, indexName);

        assertFalse(rows.isEmpty(), "Missing index " + indexName + " on " + table);
        List<String> columns = rows.stream()
                .map(row -> (String) row.get("COLUMN_NAME"))
                .toList();
        return columns.equals(List.of(expectedColumns));
    }

    private boolean foreignKeyExists(String table, String column, String referencedTable) {
        List<String> referenced = jdbcTemplate.queryForList(
                """
                SELECT kcu.REFERENCED_TABLE_NAME
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.TABLE_CONSTRAINTS tc
                  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
                  AND kcu.TABLE_SCHEMA = DATABASE()
                  AND kcu.TABLE_NAME = ?
                  AND kcu.COLUMN_NAME = ?
                """,
                String.class, table, column);
        return referenced.contains(referencedTable);
    }
}
