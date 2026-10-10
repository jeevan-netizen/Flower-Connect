import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { HomePage } from "./HomePage";
import { useLocationStore } from "@/features/location/stores/location-store";
import { makeServiceLocations } from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";

/**
 * `HomePage` lazy-loads the bouquet, so these tests drive the pending state
 * rather than waiting for a chunk: the stub suspends forever on demand and is
 * released for the cases that need the scene mounted. What matters here is the
 * page's own contract — the heading, copy, location picker, calls to action,
 * feature row and decoration are outside the canvas, in the initial chunk, and
 * usable while the 3D scene is still loading or has failed.
 */
const hero = vi.hoisted(() => ({ pending: true }));

vi.mock("@/features/home/hero/BouquetHero", () => ({
  default: function BouquetHeroStub() {
    if (hero.pending) {
      throw new Promise<void>(() => {});
    }
    return <div data-testid="stub-hero" />;
  },
}));

/**
 * The hero's location picker reads the shared locations query and the real
 * session store; the query is stubbed so the tests drive its three states
 * (loading, loaded, failed) deterministically. `isPending` and `isSuccess` are
 * controlled explicitly because the real query distinguishes a failed fetch
 * (no data, not pending) from a loading one — the error case is exactly where
 * the data is also absent.
 */
const mockQueryState = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
  isPending: true,
  isSuccess: false,
  error: null as Error | null,
}));

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: vi.fn(() => ({
    data: mockQueryState.cities,
    isPending: mockQueryState.isPending,
    error: mockQueryState.error,
    isSuccess: mockQueryState.isSuccess,
    refetch: vi.fn(),
  })),
}));

/** Reports where the hero's search sent the visitor, query string included. */
function BrowseDestination() {
  const location = useLocation();
  return <p>Browse destination {location.search}</p>;
}

function renderHome() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/browse" element={<BrowseDestination />} />
        <Route path="/vendor/register" element={<p>Florist registration destination</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  hero.pending = false;
});

describe("HomePage", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQueryState.cities = makeServiceLocations();
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = true;
    mockQueryState.error = null;
  });

  it("renders the heading, copy and calls to action while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();
    // Let the dynamic import settle; the stub keeps suspending afterwards, so
    // the fallback is still what the visitor sees.
    await act(async () => {});

    expect(
      screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Local florists near you")).toBeInTheDocument();
    expect(screen.getByText(/discover independent florists/i)).toBeInTheDocument();
    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
  });

  it("gives the location field a real label rather than only a placeholder", () => {
    hero.pending = true;
    renderHome();

    // The hero's picker is a select over the seeded areas, not free text: a
    // customer picks where they are rather than typing it (task 4.2).
    const picker = screen.getByLabelText("Delivery location");
    expect(picker.tagName).toBe("SELECT");
    expect(
      screen.getByRole("option", { name: "Indiranagar (560038)" }),
    ).toHaveValue("3");
  });

  it("stores the chosen area for the session when the visitor picks one", async () => {
    const user = userEvent.setup();
    hero.pending = true;
    renderHome();

    await user.selectOptions(screen.getByLabelText("Delivery location"), "4");

    // The coordinates come from the server's row, and the choice is persisted
    // to sessionStorage — it survives navigation and a refresh in this tab.
    expect(useLocationStore.getState().selected).toEqual({
      id: 4,
      city: "Bengaluru",
      area: "Koramangala",
      pincode: "560034",
      latitude: 12.9352,
      longitude: 77.6245,
    });
    const stored = JSON.parse(sessionStorage.getItem("fc-location") ?? "null");
    expect(stored.state.selected.area).toBe("Koramangala");
    // The select reflects the store, so the picker and the shell cannot
    // disagree about where the visitor is.
    expect(screen.getByLabelText("Delivery location")).toHaveValue("4");
  });

  it("navigates to the browse route with the session holding the selection", async () => {
    const user = userEvent.setup();
    hero.pending = true;
    renderHome();

    await user.selectOptions(screen.getByLabelText("Delivery location"), "3");
    await user.click(screen.getByRole("button", { name: "Explore Flowers" }));

    // No query string: the session store is the single source, and a duplicate
    // ?locationId= would be a second copy that can disagree with it.
    expect(await screen.findByText("Browse destination")).toBeInTheDocument();
    expect(useLocationStore.getState().selected?.id).toBe(3);
  });

  it("still navigates to the browse route with no area chosen", async () => {
    const user = userEvent.setup();
    hero.pending = true;
    renderHome();

    await user.click(screen.getByRole("button", { name: "Explore Flowers" }));

    expect(await screen.findByText("Browse destination")).toBeInTheDocument();
    expect(useLocationStore.getState().selected).toBeNull();
  });

  it("disables the picker and explains the state while the areas load", async () => {
    hero.pending = true;
    mockQueryState.cities = undefined;
    mockQueryState.isPending = true;
    mockQueryState.isSuccess = false;
    mockQueryState.error = null;
    renderHome();

    expect(screen.getByLabelText("Delivery location")).toBeDisabled();
    expect(await screen.findByText(/loading delivery areas/i)).toBeInTheDocument();
    // The rest of the hero is untouched by the location list's state.
    expect(
      screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it("explains a failed fetch and offers a retry without breaking navigation", async () => {
    const user = userEvent.setup();
    hero.pending = true;
    mockQueryState.cities = undefined;
    mockQueryState.isPending = false;
    mockQueryState.isSuccess = false;
    mockQueryState.error = new Error("Boom");
    renderHome();

    expect(await screen.findByText(/could not load delivery areas/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Delivery location")).toBeDisabled();

    // Navigation still works: a location-list failure must not take the hero
    // with it.
    await user.click(screen.getByRole("button", { name: "Explore Flowers" }));
    expect(await screen.findByText("Browse destination")).toBeInTheDocument();
  });

  it("explains an empty region instead of offering an unusable picker", async () => {
    hero.pending = true;
    mockQueryState.cities = [];
    renderHome();

    expect(await screen.findByText(/no delivery areas are available yet/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Delivery location")).toBeDisabled();
  });

  it("points the florist call to action at the existing vendor registration route", () => {
    hero.pending = true;
    renderHome();

    expect(screen.getByRole("link", { name: /become a florist/i })).toHaveAttribute(
      "href",
      "/vendor/register",
    );
    expect(screen.getByText(/open your shop in minutes/i)).toBeInTheDocument();
  });

  it("keeps the florist call to action usable while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.click(screen.getByRole("link", { name: /become a florist/i }));

    expect(await screen.findByText("Florist registration destination")).toBeInTheDocument();
  });

  it("describes the bouquet in text for anyone who never sees the canvas", () => {
    hero.pending = true;
    renderHome();

    expect(screen.getByText(/hand-tied bouquet of rose, blush and gold/i)).toBeInTheDocument();
  });

  it("hides the decorative background layer from assistive technology and the pointer", () => {
    hero.pending = true;
    const { container } = renderHome();

    const backdrop = container.querySelector('[aria-hidden="true"].pointer-events-none');
    expect(backdrop).not.toBeNull();
    expect(backdrop?.className).toContain("absolute");
  });

  it("lists the four capabilities with no invented numbers or social proof", () => {
    hero.pending = true;
    renderHome();

    expect(
      screen.getByRole("heading", { name: /what flowerconnect does/i }),
    ).toBeInTheDocument();
    for (const title of [
      "Local florists",
      "Same-day delivery",
      "Scheduled delivery",
      "Simple ordering",
    ]) {
      expect(screen.getByText(title)).toBeInTheDocument();
    }
    expect(screen.queryByText(/\d+\+?\s*(rating|review|customer|florist)/i)).not.toBeInTheDocument();
  });

  it("mounts the scene without disturbing the page once it has loaded", async () => {
    hero.pending = false;
    renderHome();

    expect(await screen.findByTestId("stub-hero")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /become a florist/i })).toHaveAttribute(
      "href",
      "/vendor/register",
    );
  });

  it("composes the shared press feedback and focus ring on the search button", () => {
    hero.pending = true;
    renderHome();

    const cta = screen.getByRole("button", { name: "Explore Flowers" });
    expect(cta.className).toContain("active:scale-press");
    expect(cta.className).toContain("motion-reduce:active:scale-100");
    expect(cta.className).toContain("focus-visible:ring-bolder-rose");
  });

  it("gives every hero control a visible focus indicator", () => {
    hero.pending = true;
    renderHome();

    // The picker's own outline is suppressed because the pill around it lights
    // up instead — so the ring has to be asserted on the form, not on the
    // control, or the suppression would be an invisible focus state.
    const form = screen.getByRole("search");
    expect(form.className).toContain("focus-within:ring-bolder-rose");
    expect(screen.getByLabelText("Delivery location").className).toContain("outline-none");

    for (const control of [
      screen.getByRole("button", { name: "Explore Flowers" }),
      screen.getByRole("link", { name: /become a florist/i }),
    ]) {
      expect(control.className).toContain("focus-visible:ring-bolder-rose");
    }
  });
});
