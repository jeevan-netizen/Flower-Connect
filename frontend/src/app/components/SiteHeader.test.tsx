import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test/render";
import { WIDE_HEADER_QUERY } from "@/app/hooks/useMediaQuery";
import { useLocationStore } from "@/features/location/stores/location-store";
import type { ServiceLocationGroup } from "@/features/location/types";
import type { UserResponse } from "@/features/auth/types";

/**
 * The header's two navigation renderings.
 *
 * The links do not fit on one row below 1280px, so the header renders the bar or
 * a disclosure panel, never both. That is the behaviour worth pinning: a second
 * copy hidden with CSS would still be a second copy in the DOM for every query,
 * test and assistive technology to reason around. The shared stub in
 * `src/test/setup.ts` reports a desktop viewport, so the bar cases need no
 * setup and the panel cases narrow it.
 */

const mockState = vi.hoisted(() => ({
  isAuthenticated: false,
  user: null as UserResponse | null,
  logout: vi.fn(),
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector?: (s: typeof mockState) => unknown) => {
    const state = {
      isAuthenticated: mockState.isAuthenticated,
      user: mockState.user,
      logout: mockState.logout,
    };
    return selector ? selector(state) : state;
  },
}));

/**
 * The header's location control reads the shared locations query and the real
 * session store, so the query is stubbed here (the store is exercised through
 * its own suite) and both are reset before every test.
 */
const mockQueryState = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
}));

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: () => ({
    data: mockQueryState.cities,
    isPending: mockQueryState.cities === undefined,
    error: null,
    isSuccess: mockQueryState.cities !== undefined,
    refetch: vi.fn(),
  }),
}));

vi.mock("@/features/vendor/queries", () => ({ clearVendorCache: vi.fn() }));
vi.mock("@/features/admin/queries", () => ({ clearAdminCache: vi.fn() }));

const { SiteHeader } = await import("@/app/components/SiteHeader");

/** Report a viewport narrower than the header's breakpoint. */
function narrowViewport() {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }));
}

function florist(): UserResponse {
  return {
    id: 1,
    email: "petal@example.com",
    fullName: "Petal Owner",
    phone: null,
    role: "FLORIST",
    createdAt: "2026-09-01",
  };
}

function renderAt(path = "/") {
  // The header stays mounted on both routes: it is the shell, so navigating must
  // not be what removes the toggle the assertion is about.
  const shell = () => (
    <>
      <SiteHeader />
      <Outlet />
    </>
  );
  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: shell(),
        children: [
          { index: true, element: <p>Home page</p> },
          { path: "browse", element: <p>Browse page</p> },
        ],
      },
    ],
    { initialEntries: [path] },
  );
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe("SiteHeader navigation", () => {
  beforeEach(() => {
    mockState.isAuthenticated = false;
    mockState.user = null;
    mockState.logout.mockClear();
    vi.unstubAllGlobals();
    // The location control's session store is real here; every test starts with
    // no location chosen and a clean storage.
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQueryState.cities = undefined;
  });

  it("shows the bar and no menu button on a wide viewport", () => {
    renderAt();

    expect(screen.getAllByRole("link", { name: "Browse" })).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: /open menu/i }),
    ).not.toBeInTheDocument();
  });

  it("collapses to a menu button on a narrow viewport, with no links beside it", () => {
    narrowViewport();

    renderAt();

    expect(screen.getByRole("button", { name: /open menu/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(screen.queryByRole("link", { name: "Browse" })).not.toBeInTheDocument();
  });

  it("reveals exactly the bar's links in the panel", async () => {
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));

    expect(
      screen.getByRole("button", { name: /close menu/i }),
    ).toHaveAttribute("aria-expanded", "true");
    for (const name of ["Home", "Browse", "Orders", "Register", "For florists", "Login"]) {
      expect(screen.getAllByRole("link", { name })).toHaveLength(1);
    }
  });

  it("closes the panel on Escape and returns focus to the toggle", async () => {
    narrowViewport();
    renderAt();

    const toggle = screen.getByRole("button", { name: /open menu/i });
    await userEvent.click(toggle);
    await userEvent.keyboard("{Escape}");

    expect(screen.getByRole("button", { name: /open menu/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(toggle).toHaveFocus();
  });

  it("closes the panel after following one of its links", async () => {
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));
    await userEvent.click(screen.getByRole("link", { name: "Browse" }));

    await waitFor(() => {
      expect(screen.getByText("Browse page")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /open menu/i }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("offers the signed-in links, and no vendor link to a customer", async () => {
    mockState.isAuthenticated = true;
    mockState.user = { ...florist(), role: "CUSTOMER" };
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));

    expect(screen.getByRole("link", { name: "Cart" })).toHaveAttribute("href", "/cart");
    expect(screen.getByRole("button", { name: /log out/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Vendor" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Register" })).not.toBeInTheDocument();
  });

  it("offers the florist and admin areas to the roles that own them", async () => {
    mockState.isAuthenticated = true;
    mockState.user = florist();
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));

    expect(screen.getByRole("link", { name: "Vendor" })).toHaveAttribute("href", "/vendor");
    expect(screen.queryByRole("link", { name: "Admin" })).not.toBeInTheDocument();
  });

  it("logs out from the panel too", async () => {
    mockState.isAuthenticated = true;
    mockState.user = florist();
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));
    await userEvent.click(screen.getByRole("button", { name: /log out/i }));

    expect(mockState.logout).toHaveBeenCalledTimes(1);
  });

  it("names the breakpoint the bar and the stylesheet share", () => {
    // `xl` in Tailwind is 1280px. If either moves, this fails rather than the
    // two silently disagreeing.
    expect(WIDE_HEADER_QUERY).toBe("(min-width: 1280px)");
  });

  it("keeps the delivery-location control in the shell on every viewport", async () => {
    renderAt();

    // The chosen area is the context the whole marketplace runs on, so the
    // control lives in the top bar rather than behind the mobile menu.
    expect(screen.getByRole("button", { name: /delivery location/i })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /delivery location/i }));

    expect(
      screen.getByRole("button", { name: /delivery location/i }),
    ).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("closes the mobile menu when the location panel opens", async () => {
    narrowViewport();
    renderAt();

    await userEvent.click(screen.getByRole("button", { name: /open menu/i }));
    await userEvent.click(screen.getByRole("button", { name: /delivery location/i }));

    // Two full-width panels anchored to the same header would overlap, so
    // opening one closes the other.
    expect(screen.getByRole("button", { name: /open menu/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });

  it("shows the chosen area once one has been picked in this tab", async () => {
    useLocationStore.setState({
      selected: {
        id: 3,
        city: "Bengaluru",
        area: "Indiranagar",
        pincode: "560038",
        latitude: 12.971199,
        longitude: 77.640586,
      },
    });

    renderAt();

    expect(screen.getByRole("button", { name: /delivery location/i })).toHaveTextContent(
      "Indiranagar, 560038",
    );
  });
});
