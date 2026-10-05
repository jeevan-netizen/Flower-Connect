import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApprovedVendorGate } from "@/features/vendor/components/ApprovedVendorGate";
import { renderWithProviders } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import { businessError } from "@/test/api-errors";
import type { VendorProfile } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
}));

function renderGate(profile: VendorProfile = makeVendorProfile()) {
  mockFetch.mockResolvedValue(profile);
  return renderWithProviders(
    <ApprovedVendorGate>
      <p>Catalog contents</p>
    </ApprovedVendorGate>,
  );
}

describe("ApprovedVendorGate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the guarded screen for an approved vendor", async () => {
    renderGate();

    expect(await screen.findByText("Catalog contents")).toBeInTheDocument();
    expect(screen.queryByText(/catalog unavailable/i)).not.toBeInTheDocument();
  });

  it("withholds the screen and explains why from a pending vendor", async () => {
    renderGate(makeVendorProfile({ status: "PENDING_APPROVAL" }));

    expect(await screen.findByText(/catalog unavailable/i)).toBeInTheDocument();
    expect(
      screen.getByText(/available to approved vendors only/i),
    ).toBeInTheDocument();
    expect(screen.queryByText("Catalog contents")).not.toBeInTheDocument();
  });

  it("withholds the screen from a rejected vendor, naming the state", async () => {
    renderGate(makeVendorProfile({ status: "REJECTED" }));

    expect(await screen.findByText(/catalog unavailable/i)).toBeInTheDocument();
    expect(screen.getByText(/your application was rejected/i)).toBeInTheDocument();
    expect(screen.queryByText("Catalog contents")).not.toBeInTheDocument();
  });

  it("withholds the screen from a suspended vendor rather than offering empty tables", async () => {
    renderGate(makeVendorProfile({ status: "SUSPENDED" }));

    expect(await screen.findByText(/catalog unavailable/i)).toBeInTheDocument();
    expect(screen.queryByText("Catalog contents")).not.toBeInTheDocument();
  });

  it("shows a loading state before the approval state is known", () => {
    mockFetch.mockImplementation(() => new Promise(() => {}));

    renderWithProviders(
      <ApprovedVendorGate>
        <p>Catalog contents</p>
      </ApprovedVendorGate>,
    );

    expect(screen.getByText(/loading your catalog/i)).toBeInTheDocument();
    expect(screen.queryByText("Catalog contents")).not.toBeInTheDocument();
  });

  it("surfaces a failed profile read instead of assuming approval", async () => {
    mockFetch.mockRejectedValue(
      businessError(500, "INTERNAL_ERROR", "An unexpected error occurred"),
    );

    renderWithProviders(
      <ApprovedVendorGate>
        <p>Catalog contents</p>
      </ApprovedVendorGate>,
    );

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
    expect(screen.queryByText("Catalog contents")).not.toBeInTheDocument();
  });

  it("offers a retry that re-reads the profile rather than a page reload", async () => {
    mockFetch.mockRejectedValueOnce(businessError(500, "INTERNAL_ERROR", "An unexpected error occurred"));

    renderWithProviders(
      <ApprovedVendorGate>
        <p>Catalog contents</p>
      </ApprovedVendorGate>,
    );

    await screen.findByText(/something went wrong/i);

    mockFetch.mockResolvedValue(makeVendorProfile());
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => {
      expect(screen.getByText("Catalog contents")).toBeInTheDocument();
    });
  });

  it("costs one profile request for the whole vendor area, not one per gate", async () => {
    renderGate();

    await screen.findByText("Catalog contents");

    expect(mockFetch).toHaveBeenCalledTimes(1);
  });
});