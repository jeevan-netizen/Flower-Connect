import { describe, it, expect } from "vitest";
import {
  countOpenDays,
  formatDateTime,
  formatFileSize,
  formatLocalDate,
  formatMoney,
  formatMovementActor,
  formatMovementType,
  formatProductStatus,
  formatQuantityDelta,
  formatRating,
  formatStatus,
  formatStockSummary,
  isPastExpiryDate,
  normalizeHours,
  toApiTime,
  toTimeInputValue,
  weekdayLabel,
} from "@/features/vendor/format";
import { makeInventory, makeStockMovement, makeVendorHours } from "@/test/factories";
import type { ProductStatus, StockMovement } from "@/features/vendor/types";

describe("money and rating formatting", () => {
  it("formats rupees with two decimals", () => {
    expect(formatMoney(1000)).toMatch(/1,000\.00/);
  });

  it("distinguishes an unset optional amount from zero", () => {
    expect(formatMoney(null)).toBe("Not set");
    expect(formatMoney(0)).not.toBe("Not set");
  });

  it("reports a null average rating as no reviews", () => {
    expect(formatRating(null, 0)).toBe("No reviews yet");
  });

  it("reports the average and count once reviews exist", () => {
    expect(formatRating(4.5, 2)).toBe("4.5 from 2 reviews");
    expect(formatRating(4.5, 1)).toBe("4.5 from 1 review");
  });
});

describe("status labels", () => {
  it("maps every backend status to vendor-facing wording", () => {
    expect(formatStatus("PENDING_APPROVAL")).toBe("Awaiting approval");
    expect(formatStatus("APPROVED")).toBe("Approved");
    expect(formatStatus("REJECTED")).toBe("Rejected");
    expect(formatStatus("SUSPENDED")).toBe("Suspended");
  });
});

describe("time conversion", () => {
  it("shortens the backend LocalTime for a time input", () => {
    expect(toTimeInputValue("09:30:00")).toBe("09:30");
  });

  it("returns an empty string for a null time", () => {
    expect(toTimeInputValue(null)).toBe("");
  });

  it("expands a time input value to the backend representation", () => {
    expect(toApiTime("09:30")).toBe("09:30:00");
  });

  it("passes an already-expanded value through unchanged", () => {
    expect(toApiTime("09:30:00")).toBe("09:30:00");
  });

  it("maps an empty time input to null", () => {
    expect(toApiTime("")).toBeNull();
  });
});

describe("normalizeHours", () => {
  it("always returns a full seven-day week", () => {
    const normalized = normalizeHours([makeVendorHours()]);

    expect(normalized).toHaveLength(7);
    expect(normalized.map((day) => day.weekday)).toEqual([
      "MONDAY",
      "TUESDAY",
      "WEDNESDAY",
      "THURSDAY",
      "FRIDAY",
      "SATURDAY",
      "SUNDAY",
    ]);
  });

  it("treats a day with no stored row as closed", () => {
    const normalized = normalizeHours([makeVendorHours({ weekday: "MONDAY" })]);

    expect(normalized[0].closed).toBe(false);
    expect(normalized[1]).toEqual({ weekday: "TUESDAY", openTime: null, closeTime: null, closed: true });
  });

  it("handles an absent week", () => {
    expect(normalizeHours(undefined).every((day) => day.closed)).toBe(true);
  });

  it("counts only days that are open", () => {
    expect(countOpenDays(normalizeHours([makeVendorHours()]))).toBe(1);
    expect(countOpenDays([])).toBe(0);
  });
});

describe("weekdayLabel", () => {
  it("uses full day names", () => {
    expect(weekdayLabel("MONDAY")).toBe("Monday");
    expect(weekdayLabel("SUNDAY")).toBe("Sunday");
  });
});

/* Catalog and inventory formatting (plan task 3.9). */

describe("formatProductStatus", () => {
  it("labels every product status in vendor terms, not enum terms", () => {
    const labels: Record<ProductStatus, string> = {
      DRAFT: formatProductStatus("DRAFT"),
      ACTIVE: formatProductStatus("ACTIVE"),
      INACTIVE: formatProductStatus("INACTIVE"),
      ARCHIVED: formatProductStatus("ARCHIVED"),
    };

    expect(Object.values(labels)).toHaveLength(new Set(Object.values(labels)).size);
    expect(labels.ACTIVE).toBe("Active");
    expect(labels.INACTIVE).toBe("Inactive");
  });
});

describe("formatStockSummary", () => {
  it("says so when the product has no inventory row yet", () => {
    expect(formatStockSummary(null)).toBe("No inventory row");
  });

  it("shows available units alone when nothing is reserved", () => {
    expect(formatStockSummary(makeInventory({ available: 12 }))).toBe("12 available");
  });

  it("names the reserved count when there is one, because it cannot be spent", () => {
    expect(formatStockSummary(makeInventory({ available: 10, reservedQuantity: 2 }))).toBe(
      "10 available (2 reserved)",
    );
  });
});

describe("formatQuantityDelta", () => {
  it("keeps the sign, so the number is not read against the movement type", () => {
    expect(formatQuantityDelta(5)).toBe("+5");
    expect(formatQuantityDelta(-3)).toBe("-3");
  });
});

describe("formatMovementType", () => {
  it("names the vendor-triggered movements", () => {
    expect(formatMovementType("STOCK_IN")).toBe("Stock in");
    expect(formatMovementType("STOCK_OUT")).toBe("Stock out");
    expect(formatMovementType("ADJUSTMENT")).toBe("Adjustment");
    // `WASTE` is the backend's word; the vendor's word is "write-off", which is what
    // the action that produces it is called.
    expect(formatMovementType("WASTE")).toBe("Write-off");
  });

  it("names the movements no vendor triggers yet, so the log stays readable", () => {
    expect(formatMovementType("RESERVE")).toBeTruthy();
    expect(formatMovementType("SALE_ONLINE")).toBeTruthy();
    expect(formatMovementType("RESTOCK")).toBeTruthy();
  });
});

describe("formatMovementActor", () => {
  it("shows the actor's email", () => {
    expect(formatMovementActor(makeStockMovement())).toContain("petal@example.com");
  });

  it("names the scheduler rather than attributing a sweep to whoever last touched the product", () => {
    const sweep = makeStockMovement({
      movementType: "WASTE",
      actorUserId: null,
      actorEmail: null,
      quantityDelta: -4,
    }) as StockMovement;

    expect(formatMovementActor(sweep)).toMatch(/schedul|automatic|system/i);
  });
});

describe("expiry date formatting", () => {
  it("shows a bare em dash rather than the word null", () => {
    expect(formatLocalDate(null)).toBe("—");
    expect(formatLocalDate("2026-10-20")).toBe("20 Oct 2026");
  });

  it("treats the stored date as the last usable day", () => {
    // The sweep writes off stock from the following day (D-25), so the stored date
    // itself is not yet expired.
    expect(isPastExpiryDate("2026-10-20", new Date("2026-10-20T09:00:00"))).toBe(false);
    expect(isPastExpiryDate("2026-10-20", new Date("2026-10-21T09:00:00"))).toBe(true);
  });

  it("has no opinion about a product with no expiry date", () => {
    expect(isPastExpiryDate(null, new Date("2026-10-21T09:00:00"))).toBe(false);
  });
});

describe("formatFileSize", () => {
  it("uses human units, so an upload budget is legible", () => {
    expect(formatFileSize(0)).toBe("0 B");
    expect(formatFileSize(84_231)).toMatch(/KB/);
    expect(formatFileSize(5 * 1024 * 1024)).toMatch(/MB/);
  });
});

describe("formatDateTime", () => {
  it("renders a movement timestamp instead of echoing the raw ISO string", () => {
    expect(formatDateTime("2026-09-05T09:05:00")).not.toContain("T");
    expect(formatDateTime("2026-09-05T09:05:00")).toMatch(/2026/);
  });
});
