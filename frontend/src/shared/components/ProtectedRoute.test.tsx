import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import type { UserResponse } from "@/features/auth/types";

interface MockAuthState {
  isAuthenticated: boolean;
  hasLoadedInitial: boolean;
  user: UserResponse | null;
  accessToken: string | null;
}

const mockState = vi.hoisted(() => ({
  isAuthenticated: false,
  hasLoadedInitial: true,
  user: null as UserResponse | null,
  accessToken: null as string | null,
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: vi.fn((selector?: (s: MockAuthState) => unknown) => {
    const state: MockAuthState = {
      isAuthenticated: mockState.isAuthenticated,
      hasLoadedInitial: mockState.hasLoadedInitial,
      user: mockState.user,
      accessToken: mockState.accessToken,
    };
    return selector ? selector(state) : state;
  }),
}));

function florist(): UserResponse {
  return { id: 4, email: "petal@example.com", fullName: "Petal Owner", phone: null, role: "FLORIST", createdAt: "2026-09-01" };
}

function customer(): UserResponse {
  return { id: 9, email: "buyer@example.com", fullName: "Buyer", phone: null, role: "CUSTOMER", createdAt: "2026-09-01" };
}

function renderGuard(roles?: string[]) {
  return render(
    <MemoryRouter initialEntries={["/vendor"]}>
      <Routes>
        <Route
          path="/vendor"
          element={
            <ProtectedRoute roles={roles}>
              <div data-testid="vendor-area">Vendor area</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div data-testid="login-page">Login</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    mockState.isAuthenticated = false;
    mockState.hasLoadedInitial = true;
    mockState.user = null;
    mockState.accessToken = null;
  });

  it("blocks unauthenticated users from a vendor route", async () => {
    renderGuard(["FLORIST"]);

    await vi.waitFor(() => {
      expect(screen.queryByTestId("vendor-area")).not.toBeInTheDocument();
    });
  });

  it("lets a FLORIST through", () => {
    mockState.isAuthenticated = true;
    mockState.accessToken = "token";
    mockState.user = florist();

    renderGuard(["FLORIST"]);

    expect(screen.getByTestId("vendor-area")).toBeInTheDocument();
  });

  it("blocks a CUSTOMER from a vendor route and says why", async () => {
    mockState.isAuthenticated = true;
    mockState.accessToken = "token";
    mockState.user = customer();

    renderGuard(["FLORIST"]);

    expect(screen.queryByTestId("vendor-area")).not.toBeInTheDocument();
    expect(await screen.findByText(/does not have access to this area/i)).toBeInTheDocument();
  });

  it("blocks an ADMIN from the vendor route (Phase 2.10 owns admin UI separately)", () => {
    mockState.isAuthenticated = true;
    mockState.accessToken = "token";
    mockState.user = { ...florist(), role: "ADMIN" };

    renderGuard(["FLORIST"]);

    expect(screen.queryByTestId("vendor-area")).not.toBeInTheDocument();
  });

  it("holds the spinner until the initial session restore finishes", () => {
    mockState.hasLoadedInitial = false;

    renderGuard(["FLORIST"]);

    expect(screen.queryByTestId("vendor-area")).not.toBeInTheDocument();
    expect(screen.queryByText(/does not have access/i)).not.toBeInTheDocument();
  });

  it("allows any authenticated user when no roles are supplied", () => {
    mockState.isAuthenticated = true;
    mockState.user = customer();

    renderGuard();

    expect(screen.getByTestId("vendor-area")).toBeInTheDocument();
  });
});
