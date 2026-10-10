import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { apiError, apiErrorFor, businessError, vendorNotApprovedError } from "@/test/api-errors";

describe("VendorErrorState", () => {
  it("guides a not-approved vendor instead of implying they must sign in again", () => {
    render(<VendorErrorState error={vendorNotApprovedError()} />);

    expect(screen.getByText(/approval required/i)).toBeInTheDocument();
    expect(screen.getByText(/approved vendors only/i)).toBeInTheDocument();
    expect(screen.queryByText(/sign in/i)).not.toBeInTheDocument();
  });

  it("explains a missing vendor profile", () => {
    render(<VendorErrorState error={businessError(404, "NOT_FOUND", "No vendor profile exists")} />);

    expect(screen.getByText(/no vendor profile/i)).toBeInTheDocument();
  });

  it("does not blame the account for a missing product", () => {
    render(
      <VendorErrorState
        error={apiErrorFor("/vendors/products/101", 404, {
          code: "NOT_FOUND",
          message: "Product not found",
        })}
      />,
    );

    // The same status, a different resource: "contact support" would send a vendor
    // with a working profile to the wrong place.
    expect(screen.queryByText(/no vendor profile/i)).not.toBeInTheDocument();
    expect(screen.getByText(/product not found/i)).toBeInTheDocument();
  });

  it("distinguishes a role failure from an approval failure", () => {
    render(<VendorErrorState error={businessError(403, "FORBIDDEN", "Forbidden")} />);

    expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    expect(screen.queryByText(/approval required/i)).not.toBeInTheDocument();
  });

  it("shows the backend message for any other failure", () => {
    render(<VendorErrorState error={businessError(409, "CONFLICT", "Profile changed elsewhere")} />);

    expect(screen.getByText(/profile changed elsewhere/i)).toBeInTheDocument();
  });

  it("offers a retry only when one is possible", async () => {
    const onRetry = vi.fn();
    render(<VendorErrorState error={apiError(500, { message: "Boom" })} onRetry={onRetry} />);

    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
