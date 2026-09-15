import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "@/features/auth/pages/LoginPage";

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

const mockAuthStore = vi.hoisted(() => vi.fn());
const mockGetState = vi.hoisted(() => vi.fn());

// eslint-disable-next-line @typescript-eslint/no-explicit-any
(mockAuthStore as any).getState = mockGetState;

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: mockAuthStore,
}));

function setMockState(overrides: Partial<AuthState>) {
  const state: AuthState = {
    accessToken: "test-access-token",
    refreshToken: "test-refresh-token",
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    hasLoadedInitial: true,
    login: vi.fn(),
    register: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    loadCurrentUser: vi.fn(),
    setAuth: vi.fn(),
    setError: vi.fn(),
    ...overrides,
  };

  mockAuthStore.mockImplementation((selector?: (s: AuthState) => unknown) => {
    return selector ? selector(state) : state;
  });
  mockGetState.mockReturnValue(state);
  return state;
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setMockState({});
  });

  it("should render email and password fields", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("should show validation errors for empty fields", async () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
  });

  it("should show validation error for invalid email", async () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/email address/i), "not-an-email");
    await userEvent.type(screen.getByLabelText(/password/i), "password123");

    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument();
  });

  it("should show validation error for missing password", async () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/email address/i), "user@test.com");

    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/password is required/i)).toBeInTheDocument();
  });

  it("should show backend error message", () => {
    setMockState({ error: "Invalid email or password" });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
  });

  it("should disable submit button while loading", () => {
    setMockState({ isLoading: true });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    const submitButton = screen.getByRole("button", { name: /signing in/i });
    expect(submitButton).toBeDisabled();
  });

  it("should call login with correct credentials", async () => {
    const mockLogin = vi.fn().mockResolvedValueOnce(undefined);
    setMockState({ login: mockLogin });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/email address/i), "user@test.com");
    await userEvent.type(screen.getByLabelText(/password/i), "password123");

    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith("user@test.com", "password123");
    });
  });

  it("should show link to register page", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/create an account/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /create an account/i })).toHaveAttribute("href", "/register");
  });
});
