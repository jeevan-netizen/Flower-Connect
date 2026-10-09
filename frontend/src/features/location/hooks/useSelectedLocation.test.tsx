import { describe, it, expect, beforeEach, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";
import { useLocationStore } from "@/features/location/stores/location-store";
import { formatSelectedLocation, type SelectedLocation } from "@/features/location/types";
import type { ServiceLocationGroup } from "@/features/location/types";
import { renderWithProviders } from "@/test/render";
import { makeServiceLocations } from "@/test/factories";

/**
 * The locations query is stubbed, not the store: the staleness rule under test
 * *is* the interaction between the two, so both sides stay real except the
 * network. `isSuccess` is what tells the hook validation is possible, which is
 * why it is controlled explicitly rather than derived.
 */
const mockQuery = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
  isSuccess: false,
}));

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: () => ({
    data: mockQuery.cities,
    isPending: mockQuery.cities === undefined,
    error: null,
    isSuccess: mockQuery.isSuccess,
    refetch: vi.fn(),
  }),
}));

const INDIRANAGAR: SelectedLocation = {
  id: 3,
  city: "Bengaluru",
  area: "Indiranagar",
  pincode: "560038",
  latitude: 12.971199,
  longitude: 77.640586,
};

/** Renders the hook's value as text, so the assertion is about what a page would show. */
function Probe() {
  const selected = useSelectedLocation();
  return <p data-testid="selection">{selected ? formatSelectedLocation(selected) : "none"}</p>;
}

describe("useSelectedLocation", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQuery.cities = undefined;
    mockQuery.isSuccess = false;
  });

  it("returns null when no location has been chosen", () => {
    renderWithProviders(<Probe />);

    expect(screen.getByTestId("selection")).toHaveTextContent("none");
  });

  it("trusts the stored selection while the list is still loading", () => {
    useLocationStore.setState({ selected: INDIRANAGAR });
    mockQuery.cities = undefined;
    mockQuery.isSuccess = false;

    renderWithProviders(<Probe />);

    // Clearing here would wipe a valid selection on every refresh: "not in the
    // list" is indistinguishable from "the list has not arrived yet".
    expect(screen.getByTestId("selection")).toHaveTextContent("Indiranagar, 560038");
    expect(useLocationStore.getState().selected).not.toBeNull();
  });

  it("returns the stored selection once the list confirms it exists", () => {
    useLocationStore.setState({ selected: INDIRANAGAR });
    mockQuery.cities = makeServiceLocations();
    mockQuery.isSuccess = true;

    renderWithProviders(<Probe />);

    expect(screen.getByTestId("selection")).toHaveTextContent("Indiranagar, 560038");
    expect(useLocationStore.getState().selected).toEqual(INDIRANAGAR);
  });

  it("clears a stored selection whose area no longer exists", async () => {
    useLocationStore.setState({ selected: INDIRANAGAR });
    // The live list no longer contains id 3.
    mockQuery.cities = makeServiceLocations({
      areas: [
        {
          id: 4,
          area: "Koramangala",
          pincode: "560034",
          latitude: 12.9352,
          longitude: 77.6245,
        },
      ],
    });
    mockQuery.isSuccess = true;

    renderWithProviders(<Probe />);

    // Displayed as "none" immediately — a page must not render an area the
    // backend would reject on discovery or search.
    expect(screen.getByTestId("selection")).toHaveTextContent("none");
    // And the corrupt entry is purged from the session, not just ignored.
    await waitFor(() => {
      expect(useLocationStore.getState().selected).toBeNull();
    });
    const stored = JSON.parse(sessionStorage.getItem("fc-location") ?? "null");
    expect(stored.state.selected).toBeNull();
  });

  it("keeps the stored selection when the list itself failed to load", () => {
    useLocationStore.setState({ selected: INDIRANAGAR });
    mockQuery.cities = undefined;
    mockQuery.isSuccess = false;

    renderWithProviders(<Probe />);

    // A failed fetch is not evidence the selection is gone, so it survives the
    // retry: the picker will revalidate when the list eventually arrives.
    expect(screen.getByTestId("selection")).toHaveTextContent("Indiranagar, 560038");
  });
});
