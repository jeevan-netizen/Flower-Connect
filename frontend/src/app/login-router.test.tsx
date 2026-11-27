import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test/render";
import { router as appRouter } from "@/app/router";
import { makeCartResponse, makeServiceLocations } from "@/test/factories";
import { useAuthStore } from "@/features/auth/stores/auth-store";

// The real auth store is used so login-driven state (isAuthenticated, user,
// justLoggedIn) updates reactively and RequireUnauth can be observed through the
// actual router. Only the API calls the store makes are stubbed. `vi.hoisted`
// keeps the mock factories free of temporal-dead-zone errors (they are hoisted
// above the `vi.mock` calls).
const mockLoginApi = vi.hoisted(() => vi.fn());
const mockRefreshApi = vi.hoisted(() => vi.fn());
const mockFetchCurrentUser = vi.hoisted(() => vi.fn());
const mockRegisterApi = vi.hoisted(() => vi.fn());
const mockLogoutApi = vi.hoisted(() => vi.fn());

vi.mock("@/features/auth/api", () => ({
  login: mockLoginApi,
  logout: mockLogoutApi,
  refresh: mockRefreshApi,
  fetchCurrentUser: mockFetchCurrentUser,
  register: mockRegisterApi,
}));

const mockFetchFarmerLinks = vi.hoisted(() => vi.fn().mockResolvedValue([]));
const mockFetchFarmerPassbook = vi.hoisted(() => vi.fn());
const mockFetchFarmerProfile = vi.hoisted(() => vi.fn());

vi.mock("@/features/farmer/api", () => ({
  fetchFarmerLinks: mockFetchFarmerLinks,
  fetchFarmerPassbook: mockFetchFarmerPassbook,
  fetchFarmerProfile: mockFetchFarmerProfile,
}));

const mockFetchCart = vi.hoisted(() => vi.fn());

vi.mock("@/features/cart/api", () => ({
  fetchCart: mockFetchCart,
  addCartItem: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

const mockFetchServiceLocations = vi.hoisted(() => vi.fn());

vi.mock("@/features/location/api", () => ({
  fetchServiceLocations: mockFetchServiceLocations,
}));

const FARMER_ENTRY_STATE = { from: "/farmer", farmerEntry: true };

function farmerUser() {
  return {
    id: 7,
    email: "farmer@test.com",
    fullName: "Demo Farmer",
    phone: "+919999999999",
    role: "FARMER",
    createdAt: "2026-01-01T00:00:00Z",
  };
}

function customerUser() {
  return {
    id: 2,
    email: "customer@test.com",
    fullName: "Cust User",
    phone: null,
    role: "CUSTOMER",
    createdAt: "2026-01-01T00:00:00Z",
  };
}

function renderLogin(entryState = FARMER_ENTRY_STATE) {
  const router = createMemoryRouter(appRouter.routes, {
    initialEntries: [{ pathname: "/login", state: entryState }],
  });
  render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

/** Start a test with an unauthenticated session mid-load, as a real page load
 *  would be: no access token, initial restore not yet finished. useInitAuth
 *  then attempts a refresh (which is mocked to reject), after which the login
 *  form renders. This avoids manufacturing a `justLoggedIn` state that did not
 *  result from an actual login in this page load. */
function startUnauthenticated() {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    isAuthenticated: false,
    error: null,
    justLoggedIn: false,
    hasLoadedInitial: false,
  });
  vi.clearAllMocks();
  mockRefreshApi.mockRejectedValue(new Error("no session"));
  mockLoginApi.mockResolvedValue({ accessToken: "at" });
  mockLogoutApi.mockResolvedValue(undefined);
  mockFetchCurrentUser.mockResolvedValue(farmerUser());
  mockFetchCart.mockResolvedValue(makeCartResponse());
  mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
  mockFetchFarmerLinks.mockResolvedValue([]);
  mockFetchFarmerPassbook.mockResolvedValue({
    linkId: 1,
    farmerId: 1,
    farmerName: "Demo Farmer",
    vendorId: 1,
    vendorName: "Green Acres Florist",
    linkStatus: "ACTIVE",
    outstandingBalance: 0,
    entries: [],
  });
}

/** Start a test with an already-authenticated session restored on page load
 *  (justLoggedIn is false because the user did not log in during this page
 *  load). This is the state for an authenticated user revisiting /login. */
function startAuthenticatedSession() {
  useAuthStore.setState({
    accessToken: "at",
    user: farmerUser(),
    isAuthenticated: true,
    error: null,
    justLoggedIn: false,
    hasLoadedInitial: true,
  });
  vi.clearAllMocks();
  mockRefreshApi.mockRejectedValue(new Error("no session"));
  mockLoginApi.mockResolvedValue({ accessToken: "at" });
  mockLogoutApi.mockResolvedValue(undefined);
  mockFetchCurrentUser.mockResolvedValue(farmerUser());
  mockFetchCart.mockResolvedValue(makeCartResponse());
  mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
  mockFetchFarmerLinks.mockResolvedValue([]);
  mockFetchFarmerPassbook.mockResolvedValue({
    linkId: 1,
    farmerId: 1,
    farmerName: "Demo Farmer",
    vendorId: 1,
    vendorName: "Green Acres Florist",
    linkStatus: "ACTIVE",
    outstandingBalance: 0,
    entries: [],
  });
}

describe("Farmer sign-in routing (/login)", () => {
  beforeEach(() => {
    startUnauthenticated();
  });

  it("sends a farmer who signs in through Farmer Login to the farmer portal", async () => {
    renderLogin();

    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(screen.getByLabelText(/email address/i), "farmer@test.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    // justLoggedIn is true during login, so RequireUnauth does not redirect;
    // LoginPage navigates to /farmer and the dashboard renders.
    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /overview/i })).toBeInTheDocument();
    });
    expect(mockLoginApi).toHaveBeenCalledWith({
      email: "farmer@test.com",
      password: "password123",
    });
  });

  it("refuses a non-farmer who uses Farmer Login and does not expose farmer data", async () => {
    mockFetchCurrentUser.mockResolvedValue(customerUser());
    const router = renderLogin();

    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(screen.getByLabelText(/email address/i), "customer@test.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    // Refusal ends the non-farmer session, then keeps the explanation visible
    // on the ordinary unauthenticated login route.
    await waitFor(() => {
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
      expect(useAuthStore.getState().justLoggedIn).toBe(false);
    });
    expect(mockLogoutApi).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /farmer sign-in is for farmer accounts/i,
    );
    expect(screen.queryByRole("heading", { name: /overview/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /my ledger/i })).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(router.state.location.state).toEqual({
      from: "/",
      farmerEntry: false,
      registeredEmail: "",
    });

    // The logged-out user follows the normal guard behavior on both auth routes;
    // no stale flag grants access as an authenticated user.
    await act(async () => {
      await router.navigate("/register");
    });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/register");
      expect(screen.getByRole("heading", { name: /create your account/i })).toBeInTheDocument();
    });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);

    await act(async () => {
      await router.navigate("/login");
    });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/login");
      expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });

  it("preserves general login behaviour when there is no farmer entry point", async () => {
    mockFetchCurrentUser.mockResolvedValue(customerUser());
    const router = renderLogin({ from: "/orders", farmerEntry: false });

    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(screen.getByLabelText(/email address/i), "customer@test.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    // General login sends the customer to their intended page ("/orders"), not
    // the farmer portal.
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/orders");
    });
    expect(screen.queryByRole("heading", { name: /overview/i })).not.toBeInTheDocument();
  });

  it("redirects an authenticated user away from /login on revisit", async () => {
    // An authenticated session restored on page load (justLoggedIn=false: the
    // user did not log in during this page load). RequireUnauth redirects
    // home immediately; the login form never renders.
    startAuthenticatedSession();
    const router = renderLogin();

    await waitFor(() => {
      expect(router.state.location.pathname).not.toBe("/login");
    });
    expect(screen.queryByRole("heading", { name: /welcome back/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/email address/i)).not.toBeInTheDocument();
    expect(mockLoginApi).not.toHaveBeenCalled();
  });
});
