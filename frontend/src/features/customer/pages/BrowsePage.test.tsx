import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowsePage } from "@/features/customer/pages/BrowsePage";
import { useLocationStore } from "@/features/location/stores/location-store";
import { renderWithProviders } from "@/test/render";
import { makeDiscoveryVendor, makePage, makeServiceLocations } from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";

const mockFetchDiscovery = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/api", () => ({
  fetchDiscovery: mockFetchDiscovery,
  fetchSearch: vi.fn(),
  fetchStorefront: vi.fn(),
  fetchPublicProduct: vi.fn(),
  fetchCategories: vi.fn(),
}));

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

const KORAMANGALA = {
  id: 4,
  city: "Bengaluru",
  area: "Koramangala",
  pincode: "560034",
  latitude: 12.9352,
  longitude: 77.6245,
};

describe("BrowsePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQuery.cities = makeServiceLocations();
    mockFetchDiscovery.mockResolvedValue(makePage([makeDiscoveryVendor()]));
  });

  it("asks for a delivery area when none is chosen, and makes no discovery request", () => {
    renderWithProviders(<BrowsePage />);

    expect(
      screen.getByRole("heading", { name: "Choose your delivery area" }),
    ).toBeInTheDocument();
    expect(mockFetchDiscovery).not.toHaveBeenCalled();
  });

  it("lists the florists near the chosen area", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithProviders(<BrowsePage />);

    expect(await screen.findByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    expect(mockFetchDiscovery).toHaveBeenCalledWith(4, 0, 20, expect.anything());
  });

  it("says which area the list is for", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithProviders(<BrowsePage />);

    expect(
      await screen.findByText("Delivering to Koramangala, 560034"),
    ).toBeInTheDocument();
  });

  it("offers a way into product search", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithProviders(<BrowsePage />);

    expect(await screen.findByRole("link", { name: "Search all products" })).toHaveAttribute(
      "href",
      "/search",
    );
  });

  it("says so when no florist delivers to the area", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchDiscovery.mockResolvedValue(makePage([]));

    renderWithProviders(<BrowsePage />);

    expect(
      await screen.findByText("No florists deliver to Koramangala yet"),
    ).toBeInTheDocument();
  });

  it("shows an error state when discovery fails", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchDiscovery.mockRejectedValue(new Error("boom"));

    renderWithProviders(<BrowsePage />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("pages through the florists", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchDiscovery.mockResolvedValue(
      makePage([makeDiscoveryVendor()], { page: 0, size: 20, totalElements: 40, totalPages: 2, last: false }),
    );

    renderWithProviders(<BrowsePage />);

    await screen.findByRole("heading", { name: "Petal & Stem" });
    await user.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => {
      expect(mockFetchDiscovery).toHaveBeenCalledWith(4, 1, 20, expect.anything());
    });
  });
});
