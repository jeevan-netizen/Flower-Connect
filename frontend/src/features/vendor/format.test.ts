import { describe, it, expect } from "vitest";
import {
  countOpenDays,
  formatMoney,
  formatRating,
  formatStatus,
  normalizeHours,
  toApiTime,
  toTimeInputValue,
  weekdayLabel,
} from "@/features/vendor/format";
import { makeVendorHours } from "@/test/factories";

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
