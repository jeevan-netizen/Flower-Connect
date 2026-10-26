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
}
