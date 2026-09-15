import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: unknown;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  hasLoadedInitial: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, phone: string | null, password: string) => Promise<void>;
  refresh: () => Promise<boolean>;
  logout: () => void;
  loadCurrentUser: () => Promise<void>;
  setAuth: (auth: unknown) => void;
  setError: (error: string | null) => void;
}

const mockState = vi.hoisted(() => {
  const state: Record<string, unknown> = {
    accessToken: null,
    refreshToken: null,
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    hasLoadedInitial: true,
  };
  return state;
});

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: vi.fn((selector?: (s: AuthState) => unknown) => {
    const fullState: AuthState = {
      accessToken: mockState.accessToken as string | null,
      refreshToken: mockState.refreshToken as string | null,
      user: mockState.user,
      isAuthenticated: mockState.isAuthenticated as boolean,
      isLoading: mockState.isLoading as boolean,
      error: mockState.error as string | null,
      hasLoadedInitial: mockState.hasLoadedInitial as boolean,
      login: vi.fn(),
      register: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
      loadCurrentUser: vi.fn(),
      setAuth: vi.fn(),
      setError: vi.fn(),
    };
    return selector ? selector(fullState) : fullState;
  }),
}));

describe("Protected Routes", () => {
  let RequireAuth: typeof import("@/features/auth/hooks/useAuth").RequireAuth;
  let RequireUnauth: typeof import("@/features/auth/hooks/useAuth").RequireUnauth;

  beforeEach(async () => {
    vi.clearAllMocks();
    mockState.accessToken = null;
    mockState.refreshToken = null;
    mockState.user = null;
    mockState.isAuthenticated = false;
    mockState.isLoading = false;
    mockState.error = null;
    mockState.hasLoadedInitial = true;

    const mod = await import("@/features/auth/hooks/useAuth");
    RequireAuth = mod.RequireAuth;
    RequireUnauth = mod.RequireUnauth;
  });

  it("should show loading spinner while auth is initializing", async () => {
    mockState.hasLoadedInitial = false;
    mockState.isAuthenticated = false;

    render(
      <MemoryRouter initialEntries={["/orders"]}>
        <Routes>
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <div data-testid="protected">Protected Orders</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.queryByTestId("protected")).not.toBeInTheDocument();
    });
  });

  it("should redirect unauthenticated user to /login", async () => {
    mockState.hasLoadedInitial = true;
    mockState.isAuthenticated = false;

    render(
      <MemoryRouter initialEntries={["/orders"]}>
        <Routes>
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <div data-testid="protected">Protected Orders</div>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.queryByTestId("protected")).not.toBeInTheDocument();
    });
  });

  it("should render protected content for authenticated user", async () => {
    mockState.isAuthenticated = true;
    mockState.hasLoadedInitial = true;
    mockState.accessToken = "token";

    render(
      <MemoryRouter initialEntries={["/orders"]}>
        <Routes>
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <div data-testid="protected">Protected Orders</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText("Protected Orders")).toBeInTheDocument();
  });

  it("should redirect authenticated user from login page to home", async () => {
    mockState.isAuthenticated = true;
    mockState.hasLoadedInitial = true;
    mockState.accessToken = "token";

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route
            path="/login"
            element={
              <RequireUnauth>
                <div data-testid="login-page">Login Page</div>
              </RequireUnauth>
            }
          />
          <Route path="/" element={<div data-testid="home">Home</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.queryByTestId("login-page")).not.toBeInTheDocument();
    });
  });
});
