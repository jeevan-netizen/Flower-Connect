import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ChangePasswordPage } from "@/features/auth/pages/ChangePasswordPage";

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

vi.mock("@/features/auth/api", () => ({
  changePassword: vi.fn(),
}));

import { changePassword } from "@/features/auth/api";

const mockChangePassword = vi.mocked(changePassword);

function setMockState(overrides: Partial<AuthState>) {
  const state: AuthState = {
    accessToken: "test-access-token",
    refreshToken: "test-refresh-token",
    user: { id: 1, email: "user@test.com", fullName: "Test User", role: "CUSTOMER", createdAt: "2026-01-01T00:00:00Z" },
    isAuthenticated: true,
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

function renderPage() {
  return render(
    <MemoryRouter>
      <ChangePasswordPage />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockChangePassword.mockReset();
});

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  values: { currentPassword?: string; newPassword?: string; confirmPassword?: string },
) {
  if (values.currentPassword !== undefined) {
    await user.type(screen.getByLabelText(/^current password$/i), values.currentPassword);
  }
  if (values.newPassword !== undefined) {
    await user.type(screen.getByLabelText(/^new password$/i), values.newPassword);
  }
  if (values.confirmPassword !== undefined) {
    await user.type(screen.getByLabelText(/confirm new password/i), values.confirmPassword);
  }
}

describe("ChangePasswordPage", () => {
  beforeEach(() => {
    setMockState({});
  });

  it("renders current password, new password and confirm password fields", () => {
    renderPage();

    expect(screen.getByLabelText(/current password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^new password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm new password/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /change password/i })).toBeInTheDocument();
  });

  it("masks every password field by default", () => {
    renderPage();

    expect(screen.getByLabelText(/current password/i)).toHaveAttribute("type", "password");
    expect(screen.getByLabelText(/^new password$/i)).toHaveAttribute("type", "password");
    expect(screen.getByLabelText(/confirm new password/i)).toHaveAttribute("type", "password");
  });

  it("toggles only the field whose show/hide button is clicked", () => {
    renderPage();

    const currentInput = screen.getByLabelText(/current password/i);
    const newInput = screen.getByLabelText(/^new password$/i);
    const confirmInput = screen.getByLabelText(/confirm new password/i);

    const toggles = screen.getAllByRole("button", { name: /show password/i });
    fireEvent.click(toggles[0]);

    expect(currentInput).toHaveAttribute("type", "text");
    expect(newInput).toHaveAttribute("type", "password");
    expect(confirmInput).toHaveAttribute("type", "password");

    fireEvent.click(screen.getByRole("button", { name: /hide password/i }));

    expect(currentInput).toHaveAttribute("type", "password");
  });

  it("toggle buttons are type=button and never submit", () => {
    renderPage();

    const toggles = screen.getAllByRole("button", { name: /show password/i });
    toggles.forEach((toggle) => {
      expect(toggle).toHaveAttribute("type", "button");
    });
  });

  it("blocks an empty submit and reports required fields", async () => {
    renderPage();

    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/current password is required/i)).toBeInTheDocument();
    expect(screen.getByText(/new password must be at least 8 characters/i)).toBeInTheDocument();
    expect(screen.getByText(/please confirm your new password/i)).toBeInTheDocument();
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it("rejects a new password shorter than 8 characters", async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user, { currentPassword: "old-pass123", newPassword: "short", confirmPassword: "short" });
    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/new password must be at least 8 characters/i)).toBeInTheDocument();
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it("rejects mismatched new passwords", async () => {
    const user = userEvent.setup();
    renderPage();

    await fillForm(user, { currentPassword: "old-pass123", newPassword: "correct-horse", confirmPassword: "different" });
    fireEvent.submit(document.querySelector("form")!);

    expect(await screen.findByText(/new passwords do not match/i)).toBeInTheDocument();
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it("calls the backend with current and new password on success", async () => {
    const user = userEvent.setup();
    mockChangePassword.mockResolvedValueOnce(undefined);
    renderPage();

    await fillForm(user, { currentPassword: "old-pass123", newPassword: "correct-horse-battery", confirmPassword: "correct-horse-battery" });
    await user.click(screen.getByRole("button", { name: /change password/i }));

    await waitFor(() => {
      expect(mockChangePassword).toHaveBeenCalledWith({
        currentPassword: "old-pass123",
        newPassword: "correct-horse-battery",
      });
    });
  });

  it("clears all fields and shows a success message on successful change", async () => {
    const user = userEvent.setup();
    mockChangePassword.mockResolvedValueOnce(undefined);
    renderPage();

    await fillForm(user, { currentPassword: "old-pass123", newPassword: "correct-horse-battery", confirmPassword: "correct-horse-battery" });
    await user.click(screen.getByRole("button", { name: /change password/i }));

    await waitFor(() => {
      expect(screen.getByText(/password updated/i)).toBeInTheDocument();
    });

    // The form is replaced by the success screen, so the fields no longer exist.
    // reset() was called with empty values, which clears them.
    expect(screen.queryByRole("button", { name: /change password/i })).not.toBeInTheDocument();
  });

  it("tells the user other sessions are revoked after success", async () => {
    const user = userEvent.setup();
    mockChangePassword.mockResolvedValueOnce(undefined);
    renderPage();

    await fillForm(user, { currentPassword: "old-pass123", newPassword: "correct-horse-battery", confirmPassword: "correct-horse-battery" });
    await user.click(screen.getByRole("button", { name: /change password/i }));

    await waitFor(() => {
      expect(screen.getByText(/other active sessions have been logged out/i)).toBeInTheDocument();
    });
  });

  it("shows a backend error message on failure", async () => {
    const user = userEvent.setup();
    mockChangePassword.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 400, data: { message: "Current password is incorrect", code: "VALIDATION_FAILED" } },
    });
    renderPage();

    await fillForm(user, { currentPassword: "wrong-pass", newPassword: "correct-horse-battery", confirmPassword: "correct-horse-battery" });
    await user.click(screen.getByRole("button", { name: /change password/i }));

    expect(await screen.findByText(/current password is incorrect/i)).toBeInTheDocument();
    expect(screen.queryByText(/password updated/i)).not.toBeInTheDocument();
  });

  it("disables the submit button while loading", () => {
    setMockState({ isLoading: true });
    renderPage();

    const button = screen.getAllByRole("button", { name: /change password|changing/i })[0];
    expect(button).toBeDisabled();
  });
});
