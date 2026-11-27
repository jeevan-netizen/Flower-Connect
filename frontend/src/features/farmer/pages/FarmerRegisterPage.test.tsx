import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { FarmerRegisterPage } from "@/features/farmer/pages/FarmerRegisterPage";

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
  registerFarmer: (
    fullName: string,
    email: string,
    phone: string | null,
    password: string,
    address?: string,
  ) => Promise<{ id: number; email: string; fullName: string; role: string } | void>;
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
    accessToken: null,
    refreshToken: null,
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    hasLoadedInitial: true,
    login: vi.fn(),
    register: vi.fn(),
    registerFarmer: vi.fn(),
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

async function submitForm() {
  const form = document.querySelector("form")!;
  fireEvent.submit(form);
  await new Promise((r) => setTimeout(r, 50));
}

describe("FarmerRegisterPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setMockState({});
  });

  it("should render all form fields", () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^address$/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Password", { exact: true })).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create account/i })).toBeInTheDocument();
  });

  it("should show validation errors for missing required fields", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await submitForm();

    expect(await screen.findByText(/full name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password must be at least 8 characters/i)).toBeInTheDocument();
    expect(screen.getByText(/please confirm your password/i)).toBeInTheDocument();
  });

  it("should show error for invalid email", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "not-an-email");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    expect(await screen.findByText(/enter a valid email address/i)).toBeInTheDocument();
  });

  it("should show error for short password", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "123");

    await submitForm();

    expect(await screen.findByText(/password must be at least 8 characters/i)).toBeInTheDocument();
  });

  it("should show error for password mismatch", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "different123");

    await submitForm();

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
  });

  it("should show error for invalid phone number", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText(/phone number/i), "abc123");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    expect(await screen.findByText(/enter a valid phone number/i)).toBeInTheDocument();
  });

  it("should allow optional phone field to be empty", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    await waitFor(() => {
      expect(screen.queryByText(/enter a valid phone number/i)).not.toBeInTheDocument();
    });
  });

  it("should allow optional address field to be empty", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    await waitFor(() => {
      expect(screen.queryByText(/address must not exceed 255 characters/i)).not.toBeInTheDocument();
    });
  });

  it("should accept address at exactly 255 characters", async () => {
    const mockRegisterFarmer = vi.fn().mockResolvedValueOnce({ id: 1, email: "test@test.com", fullName: "Test Farmer", role: "FARMER" });
    setMockState({ registerFarmer: mockRegisterFarmer });

    render(
      <MemoryRouter initialEntries={["/farmer/register"]}>
        <Routes>
          <Route path="/farmer/register" element={<FarmerRegisterPage />} />
          <Route path="/login" element={<div data-testid="login">Login</div>} />
        </Routes>
      </MemoryRouter>
    );

    const address255 = "A".repeat(255);
    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");
    await userEvent.type(screen.getByLabelText(/^address$/i), address255);

    await submitForm();

    await waitFor(() => {
      expect(mockRegisterFarmer).toHaveBeenCalledWith(
        "Test Farmer",
        "test@test.com",
        null,
        "password123",
        address255,
      );
    });
  });

  it("should reject address exceeding 255 characters", async () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");
    await userEvent.type(screen.getByLabelText(/^address$/i), "A".repeat(256));

    await submitForm();

    await waitFor(() => {
      expect(screen.getByText(/address must not exceed 255 characters/i)).toBeInTheDocument();
    });
  });

  it("should call registerFarmer with correct data on successful registration", async () => {
    const mockRegisterFarmer = vi.fn().mockResolvedValueOnce({ id: 1, email: "test@test.com", fullName: "Test Farmer", role: "FARMER" });
    setMockState({ registerFarmer: mockRegisterFarmer });

    render(
      <MemoryRouter initialEntries={["/farmer/register"]}>
        <Routes>
          <Route path="/farmer/register" element={<FarmerRegisterPage />} />
          <Route path="/login" element={<div data-testid="login">Login</div>} />
        </Routes>
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText(/phone number/i), "+1234567890");
    await userEvent.type(screen.getByLabelText(/^address$/i), "123 Farm Road");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    await waitFor(() => {
      expect(mockRegisterFarmer).toHaveBeenCalledWith(
        "Test Farmer",
        "test@test.com",
        "+1234567890",
        "password123",
        "123 Farm Road",
      );
    });
  });

  it("should show backend error for duplicate email", () => {
    setMockState({ error: "Email already in use" });

    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/email already in use/i)).toBeInTheDocument();
  });

  it("should show backend error for duplicate phone", () => {
    setMockState({ error: "Phone number already in use" });

    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/phone number already in use/i)).toBeInTheDocument();
  });

  it("should disable submit button while loading", () => {
    setMockState({ isLoading: true });

    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    const submitButton = screen.getByRole("button", { name: /creating account.../i });
    expect(submitButton).toBeDisabled();
  });

  it("should show link to login page", () => {
    render(
      <MemoryRouter>
        <FarmerRegisterPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/already have an account/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /sign in/i })).toHaveAttribute("href", "/login");
  });

  it("should navigate to login with registered email on successful registration", async () => {
    const mockRegisterFarmer = vi.fn().mockResolvedValueOnce({ id: 1, email: "test@test.com", fullName: "Test Farmer", role: "FARMER" });
    setMockState({ registerFarmer: mockRegisterFarmer });

    render(
      <MemoryRouter initialEntries={["/farmer/register"]}>
        <Routes>
          <Route path="/farmer/register" element={<FarmerRegisterPage />} />
          <Route path="/login" element={<div data-testid="login">Login</div>} />
        </Routes>
      </MemoryRouter>
    );

    await userEvent.type(screen.getByLabelText(/full name/i), "Test Farmer");
    await userEvent.type(screen.getByLabelText(/email address/i), "test@test.com");
    await userEvent.type(screen.getByLabelText("Password", { exact: true }), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");

    await submitForm();

    await waitFor(() => {
      expect(screen.getByTestId("login")).toBeInTheDocument();
    });
  });
});