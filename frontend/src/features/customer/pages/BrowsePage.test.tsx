import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { router as appRouter } from "@/app/router";

const mockFetchServiceLocations = vi.hoisted(() => vi.fn());
const mockFetchDiscovery = vi.hoisted(() => vi.fn());

const MOCK_CITIES = [
  {
    city: "Bengaluru",
    areas: [
      { id: 1, area: "Indiranagar", pincode: "560038", latitude: 12.978, longitude: 77.641 },
      { id: 2, area: "Koramangala", pincode: "560034", latitude: 12.935, longitude: 77.624 },
      { id: 3, area: "Whitefield", pincode: "560066", latitude: 12.969, longitude: 77.75 },
    ],
  },
];

const MOCK_DISCOVERY_RESPONSE = {
  content: [
    {
      id: 1,
      businessName: "Petal & Stem",
      description: "Seasonal bouquets",
      city: "Bengaluru",
      area: "Indiranagar",
      pincode: "560038",
      deliveryRadiusKm: 5,
      minOrderAmount: 300,
      baseDeliveryFee: 25,
      perKmFee: 5,
      freeDeliveryAbove: 1000,
      prepTimeMinutes: 30,
      avgRating: 4.5,
      reviewCount: 12,
      acceptingOrders: true,
      distanceKm: 2.1,
      estimatedDeliveryFee: 35.5,
    },
    {
      id: 2,
      businessName: "Bloom Box",
      description: "Modern arrangements",
      city: "Bengaluru",
      area: "Koramangala",
      pincode: "560034",
      deliveryRadiusKm: 8,
      minOrderAmount: 500,
      baseDeliveryFee: 30,
      perKmFee: 4,
      freeDeliveryAbove: 1500,
      prepTimeMinutes: 45,
      avgRating: null,
      reviewCount: 0,
      acceptingOrders: true,
      distanceKm: 4.3,
      estimatedDeliveryFee: 47.2,
    },
    {
      id: 3,
      businessName: "Rose Garden",
      description: "Classic roses",
      city: "Bengaluru",
      area: "Whitefield",
      pincode: "560066",
      deliveryRadiusKm: 10,
      minOrderAmount: 200,
      baseDeliveryFee: 20,
      perKmFee: 3,
      freeDeliveryAbove: 800,
      prepTimeMinutes: 25,
      avgRating: 4.8,
      reviewCount: 25,
      acceptingOrders: false,
      distanceKm: 6.7,
      estimatedDeliveryFee: 40.1,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 3,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};

const EMPTY_DISCOVERY_RESPONSE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  empty: true,
};

vi.mock("@/features/location/api", () => ({
  fetchServiceLocations: mockFetchServiceLocations,
}));

vi.mock("@/features/customer/api", () => ({
  fetchDiscovery: mockFetchDiscovery,
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderAt(path: string) {
  const queryClient = createTestQueryClient();
  const router = createMemoryRouter(appRouter.routes, { initialEntries: [path] });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

import { useLocationStore } from "@/features/location/stores/location-store";

describe("BrowsePage (Task 4.6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useLocationStore.getState().clear();
    mockFetchServiceLocations.mockResolvedValue(MOCK_CITIES);
    mockFetchDiscovery.mockResolvedValue(MOCK_DISCOVERY_RESPONSE);
  });

  it("renders the page heading for anonymous users", async () => {
    renderAt("/browse");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Discover Florists Near You", level: 1 })).toBeInTheDocument();
    });
  });

  it("shows the location picker when no location is selected", async () => {
    renderAt("/browse");

    await waitFor(() => {
      // Scope to the location picker section by its test id
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).toBeInTheDocument();
    });
    // The guidance text is in the results section, not the picker section
    expect(screen.getByText(/choose an area above to see florists who deliver to you/i)).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });

  it("does not fetch discovery when no location is selected", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).toBeInTheDocument();
    });
    expect(mockFetchDiscovery).not.toHaveBeenCalled();
  });

  it("shows loading skeletons while locations are loading", async () => {
    mockFetchServiceLocations.mockImplementation(() => new Promise(() => {}));

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByText(/loading delivery areas/i)).toBeInTheDocument();
    });
    const section = screen.getByTestId("browse-location-section");
    expect(within(section).getByLabelText(/delivery location/i)).toBeDisabled();
  });

  it("shows location error state when locations fetch fails", async () => {
    mockFetchServiceLocations.mockRejectedValue(new Error("Network error"));

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByText(/delivery areas could not be loaded/i)).toBeInTheDocument();
      expect(within(section).getByRole("button", { name: /try again/i })).toBeInTheDocument();
    });
  });

  it("shows vendor cards when location is selected and discovery succeeds", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).toBeInTheDocument();
    });

    // Wait for locations to load (select to be enabled)
    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    // Check that the discovery query was triggered
    await waitFor(() => {
      expect(mockFetchDiscovery).toHaveBeenCalledWith({
        locationId: 1,
        page: 0,
        size: 20,
      });
    });

    // Debug: check for any article elements
    await waitFor(() => {
      const articles = screen.getAllByRole("article");
      console.log("Articles found:", articles.length, articles.map(a => a.textContent?.slice(0, 50)));
    });

    // Use getByText for business names since getByRole name matching can be tricky
    await waitFor(() => {
      expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
      expect(screen.getByText("Bloom Box")).toBeInTheDocument();
      expect(screen.getByText("Rose Garden")).toBeInTheDocument();
    });
  });

  it("displays all required vendor card fields", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      // Use getByText for business name to find the vendor card
      const vendorCard = screen.getByText("Petal & Stem").closest("article")!;
      expect(within(vendorCard).getByText("Indiranagar, 560038")).toBeInTheDocument();
      expect(within(vendorCard).getByText("2.1 km")).toBeInTheDocument();
      expect(within(vendorCard).getByText("₹35.50")).toBeInTheDocument();
      expect(within(vendorCard).getByText("30 min prep")).toBeInTheDocument();
      expect(within(vendorCard).getByText("Min. order ₹300.00")).toBeInTheDocument();
      expect(within(vendorCard).getByText("4.5 (12 reviews)")).toBeInTheDocument();
      expect(within(vendorCard).getByText("Accepting orders")).toBeInTheDocument();
      expect(within(vendorCard).getByText("Delivery within 2.1 km radius")).toBeInTheDocument();
    });
  });

  it("shows 'New' for vendors without a rating", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      // Use getByText for business name to find the vendor card
      const vendorCard = screen.getByText("Bloom Box").closest("article")!;
      expect(within(vendorCard).getByText("New")).toBeInTheDocument();
      expect(within(vendorCard).queryByText(/reviews?/i)).not.toBeInTheDocument();
    });
  });

  it("shows 'Not accepting orders' badge when acceptingOrders is false", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText("Not accepting orders")).toBeInTheDocument();
    });
  });

  it("shows empty state when no florists deliver to the selected area", async () => {
    mockFetchDiscovery.mockResolvedValue(EMPTY_DISCOVERY_RESPONSE);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText(/no florists found/i)).toBeInTheDocument();
      expect(screen.getByText(/no florists currently deliver to indiranagar, 560038/i)).toBeInTheDocument();
    });
  });

  it("shows error state when discovery fetch fails", async () => {
    mockFetchDiscovery.mockRejectedValue(new Error("Network error"));

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText(/could not load florists/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
    });
  });

  it("resets pagination when location changes", async () => {
    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(mockFetchDiscovery).toHaveBeenCalledWith({
        locationId: 1,
        page: 0,
        size: 20,
      });
    });

    await userEvent.selectOptions(select, "2");

    await waitFor(() => {
      expect(mockFetchDiscovery).toHaveBeenCalledWith({
        locationId: 2,
        page: 0,
        size: 20,
      });
    });
  });

  it("shows pagination when there are multiple pages", async () => {
    const multiPageResponse = {
      ...MOCK_DISCOVERY_RESPONSE,
      page: 0,
      totalPages: 3,
      totalElements: 50,
      last: false,
    };
    mockFetchDiscovery.mockResolvedValue(multiPageResponse);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByRole("navigation", { name: /pagination/i })).toBeInTheDocument();
    });
  });

  it("disables pagination while fetching next page", async () => {
    let resolveNextPage: (value: unknown) => void;
    const nextPagePromise = new Promise((resolve) => { resolveNextPage = resolve; });

    // First call returns multi-page data, subsequent calls hang
    mockFetchDiscovery
      .mockResolvedValueOnce({
        ...MOCK_DISCOVERY_RESPONSE,
        page: 0,
        totalPages: 3,
        totalElements: 50,
        last: false,
      })
      .mockReturnValue(nextPagePromise);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByRole("navigation", { name: /pagination/i })).toBeInTheDocument();
    });

    // Click next page - pagination should be disabled while fetching
    const nextButton = screen.getByRole("button", { name: /next/i });
    expect(nextButton).not.toBeDisabled();
    await userEvent.click(nextButton);

    // Next button should be disabled while fetching
    await waitFor(() => {
      expect(nextButton).toBeDisabled();
    }, { timeout: 3000 });

    // Resolve the next page
    resolveNextPage!(MOCK_DISCOVERY_RESPONSE);
  });

  it("shows distance in meters when less than 1 km", async () => {
    const responseWithMeters = {
      ...MOCK_DISCOVERY_RESPONSE,
      content: [
        {
          ...MOCK_DISCOVERY_RESPONSE.content[0],
          id: 10,
          distanceKm: 0.5,
          estimatedDeliveryFee: 27.5,
        },
      ],
    };
    mockFetchDiscovery.mockResolvedValue(responseWithMeters);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText("500 m")).toBeInTheDocument();
    });
  });

  it("shows 'No minimum order' when minOrderAmount is 0", async () => {
    const responseNoMin = {
      ...MOCK_DISCOVERY_RESPONSE,
      content: [
        {
          ...MOCK_DISCOVERY_RESPONSE.content[0],
          id: 10,
          minOrderAmount: 0,
        },
      ],
    };
    mockFetchDiscovery.mockResolvedValue(responseNoMin);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText("No minimum order")).toBeInTheDocument();
    });
  });

  it("shows single review text when reviewCount is 1", async () => {
    const responseSingleReview = {
      ...MOCK_DISCOVERY_RESPONSE,
      content: [
        {
          ...MOCK_DISCOVERY_RESPONSE.content[0],
          id: 10,
          reviewCount: 1,
        },
      ],
    };
    mockFetchDiscovery.mockResolvedValue(responseSingleReview);

    renderAt("/browse");

    await waitFor(() => {
      const section = screen.getByTestId("browse-location-section");
      expect(within(section).getByLabelText(/delivery location/i)).not.toBeDisabled();
    });

    const section = screen.getByTestId("browse-location-section");
    const select = within(section).getByLabelText(/delivery location/i) as HTMLSelectElement;
    await userEvent.selectOptions(select, "1");

    await waitFor(() => {
      expect(screen.getByText(/4\.5 \(1 review\)/)).toBeInTheDocument();
    });
  });

  it("is accessible at /browse without authentication", async () => {
    renderAt("/browse");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Discover Florists Near You", level: 1 })).toBeInTheDocument();
    });
  });

  it("keeps the browse route in the customer route table", () => {
    const paths = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .map((child) => child.path);

    expect(paths).toContain("browse");
  });
});