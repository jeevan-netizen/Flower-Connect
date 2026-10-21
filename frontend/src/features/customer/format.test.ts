import { describe, it, expect } from "vitest";
import {
  formatAvailability,
  formatCustomerRating,
  formatDeliveryFee,
  formatDistanceKm,
  formatFileSize,
  formatMoney,
  formatOpeningHours,
  formatPrepTime,
  formatWeekday,
} from "@/features/customer/format";

describe("customer format helpers", () => {
  describe("formatMoney", () => {
    it("renders rupees with two decimals", () => {
      expect(formatMoney(899)).toContain("899");
      expect(formatMoney(0)).toContain("0");
    });

    it("says so when there is no amount", () => {
      expect(formatMoney(null)).toBe("Not set");
    });
  });

  describe("formatDistanceKm", () => {
    it("renders the server's distance to one decimal", () => {
      expect(formatDistanceKm(2.44)).toBe("2.4 km away");
    });

    it("says under a kilometre rather than showing a fraction", () => {
      expect(formatDistanceKm(0.4)).toBe("Under 1 km away");
    });

    it("admits when there is no distance", () => {
      expect(formatDistanceKm(null)).toBe("Distance unavailable");
    });
  });

  describe("formatCustomerRating", () => {
    it("reads as New for a florist with no rating yet", () => {
      expect(formatCustomerRating(null, 0)).toBe("New");
      expect(formatCustomerRating(0, 0)).toBe("New");
    });

    it("shows the score and the number of reviews", () => {
      expect(formatCustomerRating(4.5, 12)).toBe("4.5 (12 reviews)");
    });

    it("uses the singular for one review", () => {
      expect(formatCustomerRating(4.5, 1)).toBe("4.5 (1 review)");
    });
  });

  describe("formatPrepTime", () => {
    it("renders minutes below an hour", () => {
      expect(formatPrepTime(30)).toBe("Ready in 30 min");
    });

    it("renders hours above an hour", () => {
      expect(formatPrepTime(90)).toBe("Ready in 1.5 h");
    });

    it("admits when there is no prep time", () => {
      expect(formatPrepTime(null)).toBe("Prep time unavailable");
    });
  });

  describe("formatAvailability", () => {
    it("says in stock for a comfortable level", () => {
      expect(formatAvailability(12)).toBe("In stock");
    });

    it("counts down the last few", () => {
      expect(formatAvailability(3)).toBe("Only 3 left");
    });

    it("says out of stock at zero", () => {
      expect(formatAvailability(0)).toBe("Out of stock");
    });

    it("admits when availability is unknown", () => {
      expect(formatAvailability(null)).toBe("Availability unknown");
    });
  });

  describe("formatDeliveryFee", () => {
    it("renders the fee with two decimals", () => {
      expect(formatDeliveryFee(37.5)).toBe("Delivery 37.50");
    });

    it("admits when there is no fee", () => {
      expect(formatDeliveryFee(null)).toBe("Delivery fee unavailable");
    });
  });

  describe("formatOpeningHours", () => {
    it("shortens the backend's HH:mm:ss to HH:mm", () => {
      expect(formatOpeningHours("09:00:00", "18:00:00", false)).toBe("09:00 – 18:00");
    });

    it("says closed for a closed day", () => {
      expect(formatOpeningHours(null, null, true)).toBe("Closed");
    });

    it("says closed when the times are missing", () => {
      expect(formatOpeningHours(null, null, false)).toBe("Closed");
    });
  });

  describe("formatWeekday", () => {
    it("title-cases the backend's weekday name", () => {
      expect(formatWeekday("MONDAY")).toBe("Monday");
      expect(formatWeekday("SUNDAY")).toBe("Sunday");
    });
  });

  describe("formatFileSize", () => {
    it("renders bytes, kilobytes and megabytes", () => {
      expect(formatFileSize(512)).toBe("512 B");
      expect(formatFileSize(84_231)).toBe("82 KB");
      expect(formatFileSize(2_500_000)).toBe("2.4 MB");
    });
  });
});
