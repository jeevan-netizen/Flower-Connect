import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorDashboardPage } from "@/features/vendor/pages/VendorDashboardPage";
import { renderWithProviders } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { VendorProfile, VendorStatus } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

function setup(profile: VendorProfile = makeVendorProfile()) {
  mockFetch.mockResolvedValue(profile);
  return renderWithProviders(<VendorDashboardPage />);
}

describe("VendorDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("composes the dashboard from the single profile request", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("shows the delivery and capacity statistics", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
    // "Delivery radius" appears both as a stat card label and as a settings row,
    // so the figures are asserted by value rather than by label.
    expect(screen.getAllByText(/delivery radius/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText("5 km").length).toBeGreaterThan(0);
    expect(screen.getByText("30 min")).toBeInTheDocument();
    expect(screen.getByText("10 / slot")).toBeInTheDocument();
    expect(screen.getByText(/accepting orders/i)).toBeInTheDocument();
  });

  it("summarises the money settings", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
    expect(screen.getAllByText(/1,000\.00/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/300\.00/).length).toBeGreaterThan(0);
  });

  it("summarises the operating week", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
    expect(screen.getByText("2 of 7")).toBeInTheDocument();
    expect(screen.getByText("09:00 – 18:00")).toBeInTheDocument();
  });

  it("reports an empty review history rather than a zero rating", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByText(/no reviews yet/i)).toBeInTheDocument();
    });
  });

  it("shows the approval status badge", async () => {
    setup(makeVendorProfile({ status: "PENDING_APPROVAL" as VendorStatus }));

    await waitFor(() => {
      expect(screen.getAllByText("Awaiting approval").length).toBeGreaterThan(0);
    });
    expect(screen.getByText(/not discoverable yet/i)).toBeInTheDocument();
  });

  it("marks an approved vendor as visible to customers", async () => {
    setup(makeVendorProfile({ status: "APPROVED" as VendorStatus }));

    await waitFor(() => {
      expect(screen.getByText(/visible to customers/i)).toBeInTheDocument();
    });
  });

  it("handles a week with no open days", async () => {
    setup(
      makeVendorProfile({
        hours: [{ weekday: "MONDAY", openTime: null, closeTime: null, closed: true }],
      }),
    );

    await waitFor(() => {
      expect(screen.getByText(/no open days configured/i)).toBeInTheDocument();
    });
  });

  it("links to the three editable areas and no Phase 3 area", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /edit profile/i })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /edit delivery settings/i })).toHaveAttribute("href", "/vendor/settings");
    expect(screen.getByRole("link", { name: /edit operating hours/i })).toHaveAttribute("href", "/vendor/hours");
    expect(screen.queryByRole("link", { name: /catalog/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /inventory/i })).not.toBeInTheDocument();
  });

  it("reports a failed load with a retry affordance", async () => {
    mockFetch.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderWithProviders(<VendorDashboardPage />);

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    mockFetch.mockResolvedValue(makeVendorProfile());
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
  });
});
