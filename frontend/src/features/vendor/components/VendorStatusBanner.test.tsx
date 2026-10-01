import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VendorStatusBanner, VendorStatusBadge } from "@/features/vendor/components/VendorStatusBanner";
import type { VendorStatus } from "@/features/vendor/types";

function renderBanner(status: VendorStatus) {
  return render(<VendorStatusBanner status={status} />);
}

describe("VendorStatusBanner", () => {
  it("explains a pending application without implying access to approved features", () => {
    renderBanner("PENDING_APPROVAL");

    expect(screen.getByRole("heading", { name: /application is awaiting approval/i })).toBeInTheDocument();
    expect(screen.getByTestId("vendor-status-badge")).toHaveTextContent("Awaiting approval");
    expect(screen.getByText(/keep editing your profile/i)).toBeInTheDocument();
  });

  it("shows the rejected state and points the vendor at their editable details", () => {
    renderBanner("REJECTED");

    expect(screen.getByRole("heading", { name: /application was rejected/i })).toBeInTheDocument();
    expect(screen.getByTestId("vendor-status-badge")).toHaveTextContent("Rejected");
    expect(screen.getByText(/review your business details/i)).toBeInTheDocument();
  });

  it("states plainly that a suspended vendor loses approved-only features", () => {
    renderBanner("SUSPENDED");

    expect(screen.getByRole("heading", { name: /account is suspended/i })).toBeInTheDocument();
    expect(screen.getByText(/vendor features are unavailable/i)).toBeInTheDocument();
  });

  it("confirms an approved account", () => {
    renderBanner("APPROVED");

    expect(screen.getByRole("heading", { name: /account is approved/i })).toBeInTheDocument();
    expect(screen.getByText(/customers can discover your shop/i)).toBeInTheDocument();
  });

  it("never claims the rejection reason is shown, because the API does not expose it", () => {
    renderBanner("REJECTED");

    expect(screen.queryByText(/reason:/i)).not.toBeInTheDocument();
  });
});

describe("VendorStatusBadge", () => {
  it("renders a compact label for each status", () => {
    const { rerender } = render(<VendorStatusBadge status="APPROVED" />);
    expect(screen.getByText("Approved")).toBeInTheDocument();

    rerender(<VendorStatusBadge status="SUSPENDED" />);
    expect(screen.getByText("Suspended")).toBeInTheDocument();
  });
});
