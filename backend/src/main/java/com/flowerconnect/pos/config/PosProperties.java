package com.flowerconnect.pos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * POS walk-in sale configuration (plan Phase 11, task 11.1).
 *
 * <p>Bounds are enforced server-side on every sale request, so a client
 * cannot inflate a ticket beyond what the vendor intends to accept at the
 * counter.
 */
@Configuration
@ConfigurationProperties(prefix = "app.pos")
public class PosProperties {

    /**
     * Maximum number of distinct products allowed on one POS sale.
     */
    private int maxLinesPerSale = 50;

    /**
     * Maximum quantity of a single product on one POS sale line.
     */
    private int maxQuantityPerLine = 100;

    /**
     * Maximum span of a report's date range in days (plan task 11.5).
     * Ranges longer than this are rejected before the database is queried.
     */
    private int maxReportDays = 365;

    /**
     * Maximum number of top-selling products returned in a report (plan
     * task 11.5). Top products are ranked by quantity sold descending.
     */
    private int topProductsLimit = 5;

    public int getMaxLinesPerSale() {
        return maxLinesPerSale;
    }

    public void setMaxLinesPerSale(int maxLinesPerSale) {
        this.maxLinesPerSale = maxLinesPerSale;
    }

    public int getMaxQuantityPerLine() {
        return maxQuantityPerLine;
    }

    public void setMaxQuantityPerLine(int maxQuantityPerLine) {
        this.maxQuantityPerLine = maxQuantityPerLine;
    }

    public int getMaxReportDays() {
        return maxReportDays;
    }

    public void setMaxReportDays(int maxReportDays) {
        this.maxReportDays = maxReportDays;
    }

    public int getTopProductsLimit() {
        return topProductsLimit;
    }

    public void setTopProductsLimit(int topProductsLimit) {
        this.topProductsLimit = topProductsLimit;
    }
}
