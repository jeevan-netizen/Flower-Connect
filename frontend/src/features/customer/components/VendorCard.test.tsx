import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VendorCard } from "@/features/customer/components/VendorCard";
import type { VendorCardView } from "@/features/customer/types";

const mockVendor: VendorCardView = {
  id: 1,
  businessName: "Petal & Stem",
  area: "Indiranagar",
  pincode: "560038",
  distanceKm: 2.1,
  estimatedDeliveryFee: 35.5,
  prepTimeMinutes: 30,
  minOrderAmount: 300,
  avgRating: 4.5,
  reviewCount: 12,
  acceptingOrders: true,
};

const mockVendorNoRating: VendorCardView = {
  ...mockVendor,
  id: 2,
  businessName: "New Blooms",
  avgRating: null,
  reviewCount: 0,
};

const mockVendorNotAccepting: VendorCardView = {
  ...mockVendor,
  id: 3,
  businessName: "Closed Shop",
  acceptingOrders: false,
};

describe("VendorCard", () => {
  it("renders business name and area", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    expect(screen.getByText("Indiranagar, 560038")).toBeInTheDocument();
  });

  it("renders distance with km unit", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("2.1 km")).toBeInTheDocument();
  });

  it("renders distance in meters when less than 1 km", () => {
    const vendor = { ...mockVendor, distanceKm: 0.5 };
    render(<VendorCard vendor={vendor} />);

    expect(screen.getByText("500 m")).toBeInTheDocument();
  });

  it("renders delivery fee in INR format", () => {
    render(<VendorCard vendor={mockVendor} />);

    // formatMoney formats as ₹35.50
    expect(screen.getByText("₹35.50")).toBeInTheDocument();
  });

  it("renders preparation time", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("30 min prep")).toBeInTheDocument();
  });

  it("renders minimum order in INR format", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("Min. order ₹300.00")).toBeInTheDocument();
  });

  it("shows 'No minimum order' when minOrderAmount is 0", () => {
    const vendor = { ...mockVendor, minOrderAmount: 0 };
    render(<VendorCard vendor={vendor} />);

    expect(screen.getByText("No minimum order")).toBeInTheDocument();
  });

  it("shows rating with review count when rating exists", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("4.5 (12 reviews)")).toBeInTheDocument();
  });

  it("shows 'New' when rating is null", () => {
    render(<VendorCard vendor={mockVendorNoRating} />);

    expect(screen.getByText("New")).toBeInTheDocument();
    expect(screen.queryByText(/reviews?/i)).not.toBeInTheDocument();
  });

  it("shows single review text when reviewCount is 1", () => {
    const vendor = { ...mockVendor, reviewCount: 1 };
    render(<VendorCard vendor={vendor} />);

    expect(screen.getByText("4.5 (1 review)")).toBeInTheDocument();
  });

  it("shows 'Accepting orders' badge when acceptingOrders is true", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("Accepting orders")).toBeInTheDocument();
  });

  it("shows 'Not accepting orders' badge when acceptingOrders is false", () => {
    render(<VendorCard vendor={mockVendorNotAccepting} />);

    expect(screen.getByText("Not accepting orders")).toBeInTheDocument();
  });

  it("renders delivery radius note in footer", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByText("Delivery within 2.1 km radius")).toBeInTheDocument();
  });

  it("has article element for semantic structure", () => {
    render(<VendorCard vendor={mockVendor} />);

    expect(screen.getByRole("article")).toBeInTheDocument();
  });
});