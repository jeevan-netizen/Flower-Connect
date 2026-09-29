package com.flowerconnect.repository;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the {@code vendor_profiles} and {@code vendor_hours} tables match
 * the data model of implementation plan v2.2 tasks 2.2, 2.3 and 2.4.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VendorProfileSchemaIT extends AbstractIntegrationTest {

    private static final List<String> PROFILE_COLUMNS = List.of(
            "id", "user_id", "business_name", "description", "address_line1", "address_line2",
            "service_location_id", "latitude", "longitude", "delivery_radius_km", "logo_url",
            "status", "commission_rate", "avg_rating", "review_count",
            "min_order_amount", "base_delivery_fee", "per_km_fee", "free_delivery_above",
            "prep_time_minutes", "slot_duration_minutes", "max_orders_per_slot", "accepting_orders",
            "created_at", "updated_at");

    private static final List<String> HOURS_COLUMNS = List.of(
            "id", "vendor_profile_id", "weekday", "open_time", "close_time", "closed",
            "created_at", "updated_at");

    @Test
    void vendorProfilesShouldHaveEveryPlannedColumn() {
        for (String column : PROFILE_COLUMNS) {
            assertNotNull(columnType("vendor_profiles", column), "Missing column vendor_profiles." + column);
        }
    }

    @Test
    void vendorHoursShouldHaveEveryPlannedColumn() {
        for (String column : HOURS_COLUMNS) {
            assertNotNull(columnType("vendor_hours", column), "Missing column vendor_hours." + column);
        }
    }

    @Test
    void vendorProfilesShouldUsePlannedColumnTypes() {
        assertEquals("decimal(10,8)", columnType("vendor_profiles", "latitude"));
        assertEquals("decimal(11,8)", columnType("vendor_profiles", "longitude"));
        assertEquals("decimal(5,2)", columnType("vendor_profiles", "delivery_radius_km"));
        assertEquals("decimal(10,2)", columnType("vendor_profiles", "min_order_amount"));
        assertEquals("decimal(10,2)", columnType("vendor_profiles", "base_delivery_fee"));
        assertEquals("decimal(10,2)", columnType("vendor_profiles", "per_km_fee"));
        assertEquals("decimal(10,2)", columnType("vendor_profiles", "free_delivery_above"));
        assertEquals("decimal(5,4)", columnType("vendor_profiles", "commission_rate"));
        assertEquals("decimal(3,2)", columnType("vendor_profiles", "avg_rating"));
        assertEquals("int", columnType("vendor_profiles", "review_count"));
        assertEquals("int", columnType("vendor_profiles", "prep_time_minutes"));
        assertEquals("int", columnType("vendor_profiles", "slot_duration_minutes"));
        assertEquals("int", columnType("vendor_profiles", "max_orders_per_slot"));
        assertEquals("bit(1)", columnType("vendor_profiles", "accepting_orders"));
        assertTrue(columnType("vendor_hours", "closed").startsWith("bit"));
        assertTrue(columnType("vendor_hours", "open_time").startsWith("time"));
        assertTrue(columnType("vendor_hours", "close_time").startsWith("time"));
    }

    @Test
    void vendorProfileStatusShouldAllowOnlyPlannedStates() {
        String type = columnType("vendor_profiles", "status");

        assertTrue(type.startsWith("enum"), "status must be an enum, was: " + type);
        assertTrue(type.contains("'PENDING_APPROVAL'"));
        assertTrue(type.contains("'APPROVED'"));
        assertTrue(type.contains("'REJECTED'"));
        assertTrue(type.contains("'SUSPENDED'"));
    }

    @Test
    void vendorProfilesShouldHavePlannedDefaults() {
        assertEquals("PENDING_APPROVAL", columnDefault("vendor_profiles", "status"));
        assertEquals("0", columnDefault("vendor_profiles", "review_count"));
        assertEquals("5.00", columnDefault("vendor_profiles", "delivery_radius_km"));
        assertEquals("0.00", columnDefault("vendor_profiles", "min_order_amount"));
        assertEquals("0.00", columnDefault("vendor_profiles", "base_delivery_fee"));
        assertEquals("0.00", columnDefault("vendor_profiles", "per_km_fee"));
        assertEquals("30", columnDefault("vendor_profiles", "prep_time_minutes"));
        assertEquals("60", columnDefault("vendor_profiles", "slot_duration_minutes"));
        assertEquals("10", columnDefault("vendor_profiles", "max_orders_per_slot"));
        assertEquals("b'1'", columnDefault("vendor_profiles", "accepting_orders").toLowerCase());
        assertEquals("b'0'", columnDefault("vendor_hours", "closed").toLowerCase());
    }

    @Test
    void vendorProfileShouldBeUniquePerUser() {
        assertTrue(indexExists("vendor_profiles", "uq_vendor_profiles_user", "user_id"));
    }

    @Test
    void vendorProfileShouldBeIndexedForStatusAndGeoQueries() {
        assertTrue(indexExists("vendor_profiles", "idx_vendor_profiles_status_geo", "status", "latitude", "longitude"));
    }

    @Test
    void vendorHoursShouldBeUniquePerProfileAndWeekday() {
        assertTrue(indexExists("vendor_hours", "uq_vendor_hours_profile_weekday", "vendor_profile_id", "weekday"));
    }

    @Test
    void vendorProfileShouldReferenceUserAndServiceLocation() {
        assertTrue(foreignKeyExists("vendor_profiles", "user_id", "users"));
        assertTrue(foreignKeyExists("vendor_profiles", "service_location_id", "service_locations"));
        assertTrue(foreignKeyExists("vendor_hours", "vendor_profile_id", "vendor_profiles"));
    }

    @Test
    void vendorHoursShouldBeCascadedWhenProfileIsDeleted() {
        String deleteRule = jdbcTemplate.queryForObject(
                """
                SELECT rc.DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS rc
                WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
                  AND rc.TABLE_NAME = 'vendor_hours'
                  AND rc.REFERENCED_TABLE_NAME = 'vendor_profiles'
                """,
                String.class);

        assertNotNull(deleteRule);
        assertEquals("CASCADE", deleteRule);
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
