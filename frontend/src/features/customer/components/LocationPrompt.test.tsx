import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LocationPrompt } from "@/features/customer/components/LocationPrompt";
import { useLocationStore } from "@/features/location/stores/location-store";
import { renderWithProviders } from "@/test/render";
import { makeServiceLocations } from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";

/**
 * The locations query is stubbed, not the store: the picker and the store are
 * the two sides of the selection rule, so both stay real except the network.
 */
const mockQuery = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
}));

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: () => ({
    data: mockQuery.cities,
    isPending: mockQuery.cities === undefined,
    error: null,
    isSuccess: mockQuery.cities !== undefined,
    refetch: vi.fn(),
  }),
}));

describe("LocationPrompt", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQuery.cities = makeServiceLocations();
  });

  it("asks for a delivery area and explains why", () => {
    renderWithProviders(<LocationPrompt />);

    expect(
      screen.getByRole("heading", { name: "Choose your delivery area" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Delivery location")).toBeInTheDocument();
  });

  it("offers the seeded areas in the picker", () => {
    renderWithProviders(<LocationPrompt />);

    const picker = screen.getByLabelText("Delivery location");
    expect(picker).toBeEnabled();
    expect(
      screen.getByRole("option", { name: "Indiranagar (560038)" }),
    ).toBeInTheDocument();
  });

  it("stores the area the customer picks", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LocationPrompt />);

    await user.selectOptions(screen.getByLabelText("Delivery location"), "3");

    expect(useLocationStore.getState().selected).toEqual(
      expect.objectContaining({ id: 3, area: "Indiranagar" }),
    );
  });

  it("shows the current selection in the picker", () => {
    useLocationStore.setState({
      selected: {
        id: 3,
        city: "Bengaluru",
        area: "Indiranagar",
        pincode: "560038",
        latitude: 12.97,
        longitude: 77.64,
      },
    });

    renderWithProviders(<LocationPrompt />);

    expect(screen.getByLabelText("Delivery location")).toHaveValue("3");
  });

  it("disables the picker while the areas are loading", () => {
    mockQuery.cities = undefined;

    renderWithProviders(<LocationPrompt />);

    expect(screen.getByLabelText("Delivery location")).toBeDisabled();
  });

  it("accepts a custom heading", () => {
    renderWithProviders(<LocationPrompt title="Where should we deliver?" />);

    expect(
      screen.getByRole("heading", { name: "Where should we deliver?" }),
    ).toBeInTheDocument();
  });
});
