import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorCard } from "@/features/customer/components/VendorCard";
import { renderWithProviders } from "@/test/render";
import { makeDiscoveryVendor } from "@/test/factories";

describe("VendorCard", () => {
  it("shows what the server computed about the florist", () => {
    renderWithProviders(<VendorCard vendor={makeDiscoveryVendor()} />);

    expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    expect(screen.getByText(/Koramangala, Bengaluru 560034/)).toBeInTheDocument();
    expect(screen.getByText("2.4 km away")).toBeInTheDocument();
    expect(screen.getByText("Ready in 30 min")).toBeInTheDocument();
    expect(screen.getByText("4.5 (12 reviews)")).toBeInTheDocument();
  });

  it("reads as New for a florist with no rating yet", () => {
    renderWithProviders(
      <VendorCard vendor={makeDiscoveryVendor({ avgRating: null, reviewCount: 0 })} />,
    );

    expect(screen.getByText("New")).toBeInTheDocument();
  });

  it("links to the florist's storefront", () => {
    renderWithProviders(<VendorCard vendor={makeDiscoveryVendor({ id: 42 })} />);

    expect(screen.getByRole("link", { name: "View shop" })).toHaveAttribute(
      "href",
      "/vendors/42",
    );
  });

  it("names the minimum order and the estimated delivery fee", () => {
    renderWithProviders(
      <VendorCard
        vendor={makeDiscoveryVendor({ minOrderAmount: 300, estimatedDeliveryFee: 37 })}
      />,
    );

    expect(screen.getByText("Minimum order")).toBeInTheDocument();
    expect(screen.getByText("Delivery")).toBeInTheDocument();
  });

  it("is reachable by keyboard through its link", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorCard vendor={makeDiscoveryVendor()} />);

    await user.tab();

    expect(screen.getByRole("link", { name: "View shop" })).toHaveFocus();
  });
});
