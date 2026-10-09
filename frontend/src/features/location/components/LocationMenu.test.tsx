import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LocationMenu } from "@/features/location/components/LocationMenu";
import { useLocationStore } from "@/features/location/stores/location-store";
import { LOCATION_STORAGE_KEY } from "@/features/location/stores/location-store";
import type { SelectedLocation, ServiceLocationGroup } from "@/features/location/types";
import { renderWithProviders } from "@/test/render";
import { makeServiceLocations } from "@/test/factories";

/**
 * The locations query is stubbed; the store is real. The staleness rule under
 * test is the interaction between the two, so both sides stay real except the
 * network.
 *
 * `isPending` and `isSuccess` are controlled explicitly rather than derived
 * from the data, because the real query distinguishes all three of its states:
 * loading (`isPending`), succeeded (`isSuccess`), and failed (neither) — and
 * the error state is exactly the one where `cities` is also undefined.
 */
const mockQueryState = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
  isPending: true,
  isSuccess: false,
  error: null as Error | null,
}));
const mockRefetch = vi.hoisted(() => vi.fn());

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: () => ({
    data: mockQueryState.cities,
    isPending: mockQueryState.isPending,
    error: mockQueryState.error,
    isSuccess: mockQueryState.isSuccess,
    refetch: mockRefetch,
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

function renderMenu(onOpen?: () => void) {
  return renderWithProviders(<LocationMenu onOpen={onOpen} />);
}

describe("LocationMenu", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQueryState.cities = makeServiceLocations();
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = true;
    mockQueryState.error = null;
    mockRefetch.mockClear();
  });

  it("prompts for a location when nothing has been chosen", () => {
    renderMenu();

    const button = screen.getByRole("button", { name: /delivery location/i });
    expect(button).toHaveAccessibleName("Set your delivery location");
    expect(button).toHaveTextContent("Set location");
    expect(button).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("shows the chosen area in the shell without opening anything", () => {
    useLocationStore.setState({ selected: INDIRANAGAR });

    renderMenu();

    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveAccessibleName(
      "Delivery location: Indiranagar, 560038",
    );
    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveTextContent(
      "Indiranagar, 560038",
    );
  });

  it("opens the picker and offers the areas, then closes it again", async () => {
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Indiranagar (560038)" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("stores a chosen area for the session and closes the panel", async () => {
    const user = userEvent.setup();
    renderMenu();
    const button = screen.getByRole("button", { name: /delivery location/i });

    await user.click(button);
    await user.selectOptions(screen.getByRole("combobox"), "4");

    // The shell shows it because the store changed, never because a save was
    // announced: there is no "saved" message anywhere in the flow.
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /delivery location/i })).toHaveTextContent(
        "Koramangala, 560034",
      );
    });
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    // Coordinates come from the server's row, not from anything the UI typed.
    expect(useLocationStore.getState().selected).toEqual({
      id: 4,
      city: "Bengaluru",
      area: "Koramangala",
      pincode: "560034",
      latitude: 12.9352,
      longitude: 77.6245,
    });
    // And it survives the refresh in this tab.
    const stored = JSON.parse(sessionStorage.getItem(LOCATION_STORAGE_KEY) ?? "null");
    expect(stored.state.selected.id).toBe(4);
    // Focus returns to the button, so a keyboard user is not stranded behind a
    // panel that just closed.
    expect(button).toHaveFocus();
    expect(screen.queryByText(/saved/i)).not.toBeInTheDocument();
  });

  it("closes on Escape and returns focus to the button", async () => {
    const user = userEvent.setup();
    renderMenu();
    const button = screen.getByRole("button", { name: /delivery location/i });

    await user.click(button);
    await user.keyboard("{Escape}");

    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(button).toHaveFocus();
  });

  it("tells the shell when it opens, so the navigation panel can close", async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    renderMenu(onOpen);

    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("stays disabled with a hint while the areas load", async () => {
    const user = userEvent.setup();
    mockQueryState.cities = undefined;
    mockQueryState.isPending = true;
    mockQueryState.isSuccess = false;
    mockQueryState.error = null;

    renderMenu();
    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(screen.getByRole("combobox")).toBeDisabled();
    expect(screen.getByText("Loading delivery areas...")).toBeInTheDocument();
  });

  it("explains a failed fetch and offers a retry", async () => {
    const user = userEvent.setup();
    mockQueryState.cities = undefined;
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = false;
    mockQueryState.error = new Error("Boom");

    renderMenu();
    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(screen.getByText("Delivery areas could not be loaded.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /try again/i }));

    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it("explains an empty region instead of offering an unusable picker", async () => {
    const user = userEvent.setup();
    mockQueryState.cities = [];
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = true;
    mockQueryState.error = null;

    renderMenu();
    await user.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(screen.getByText("No delivery areas are configured yet.")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toBeDisabled();
  });

  it("drops a stale selection instead of showing an area that no longer exists", async () => {
    useLocationStore.setState({ selected: INDIRANAGAR });
    // The live list no longer contains id 3.
    mockQueryState.cities = makeServiceLocations({
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
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = true;
    mockQueryState.error = null;

    renderMenu();

    // The shell must not render a location discovery and search would reject.
    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveTextContent(
      "Set location",
    );
    // The corrupt session entry is purged, not merely ignored.
    await waitFor(() => {
      expect(useLocationStore.getState().selected).toBeNull();
    });
  });
});
