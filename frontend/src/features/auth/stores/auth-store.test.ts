import { describe, it, expect, vi, beforeEach } from "vitest";
import { login, register, refresh, logout, fetchCurrentUser } from "@/features/auth/api";
import { useAuthStore } from "@/features/auth/stores/auth-store";

vi.mock("@/features/auth/api", () => ({
  login: vi.fn(),
  register: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  fetchCurrentUser: vi.fn(),
}));

describe("Auth Store", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      hasLoadedInitial: false,
    });
  });

  it("should start with unauthenticated state", () => {
    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.hasLoadedInitial).toBe(false);
  });

  it("should set auth on setAuth", () => {
    useAuthStore.getState().setAuth({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      tokenType: "Bearer",
      expiresIn: 900000,
    });
    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("access-token");
    expect(state.refreshToken).toBe("refresh-token");
    expect(state.isAuthenticated).toBe(true);
    expect(state.error).toBeNull();
  });

  it("should clear auth on logout and call backend logout", async () => {
    useAuthStore.setState({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      user: { id: 1, email: "test@test.com", fullName: "Test", phone: null, role: "CUSTOMER", createdAt: "2024-01-01" },
      isAuthenticated: true,
      isLoading: false,
      error: null,
      hasLoadedInitial: true,
    });
    (logout as ReturnType<typeof vi.fn>).mockResolvedValueOnce(undefined as unknown as void);

    await useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(logout).toHaveBeenCalledWith({ refreshToken: "refresh-token" });
  });

  it("should login and set tokens on success", async () => {
    (login as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      tokenType: "Bearer",
      expiresIn: 900000,
    });
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: "user@test.com",
      fullName: "Test User",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2024-01-01T00:00:00",
    });

    await useAuthStore.getState().login("user@test.com", "password123");

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("new-access-token");
    expect(state.refreshToken).toBe("new-refresh-token");
    expect(state.isAuthenticated).toBe(true);
    expect(state.user).not.toBeNull();
    expect(state.user?.email).toBe("user@test.com");
    expect(state.isLoading).toBe(false);
  });

  it("should handle login failure", async () => {
    (login as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      response: { data: { message: "Invalid email or password" } },
    });

    await useAuthStore.getState().login("bad@test.com", "wrong");

    const state = useAuthStore.getState();
    expect(state.error).toBe("Invalid email or password");
    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.isLoading).toBe(false);
  });

  it("should register and set tokens on success", async () => {
    (register as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      tokenType: "Bearer",
      expiresIn: 900000,
    });
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: "new@test.com",
      fullName: "New User",
      phone: "+1234567890",
      role: "CUSTOMER",
      createdAt: "2024-01-01T00:00:00",
    });

    await useAuthStore.getState().register("New User", "new@test.com", "+1234567890", "password123");

    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("access-token");
    expect(state.isAuthenticated).toBe(true);
    expect(state.user?.email).toBe("new@test.com");
  });

  it("should refresh tokens on success", async () => {
    useAuthStore.setState({
      accessToken: "old-access-token",
      refreshToken: "old-refresh-token",
      user: null,
      isAuthenticated: true,
      isLoading: false,
      error: null,
      hasLoadedInitial: true,
    });
    (refresh as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      tokenType: "Bearer",
      expiresIn: 900000,
    });
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: "user@test.com",
      fullName: "Test User",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2024-01-01T00:00:00",
    });

    const result = await useAuthStore.getState().refresh();

    expect(result).toBe(true);
    const state = useAuthStore.getState();
    expect(state.accessToken).toBe("new-access-token");
    expect(state.refreshToken).toBe("new-refresh-token");
  });

  it("should return false and clear auth on refresh failure", async () => {
    useAuthStore.setState({
      accessToken: "old-access-token",
      refreshToken: "invalid-refresh-token",
      user: null,
      isAuthenticated: true,
      isLoading: false,
      error: null,
      hasLoadedInitial: true,
    });
    (refresh as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error("Invalid"));

    const result = await useAuthStore.getState().refresh();

    expect(result).toBe(false);
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
  });

  it("should load current user and set hasLoadedInitial", async () => {
    useAuthStore.setState({
      accessToken: "access-token",
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      hasLoadedInitial: false,
    });
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: 1,
      email: "user@test.com",
      fullName: "Test User",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2024-01-01T00:00:00",
    });

    await useAuthStore.getState().loadCurrentUser();

    const state = useAuthStore.getState();
    expect(state.user?.email).toBe("user@test.com");
    expect(state.isAuthenticated).toBe(true);
    expect(state.hasLoadedInitial).toBe(true);
  });

  it("should not load current user without access token", async () => {
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ id: 1 });

    await useAuthStore.getState().loadCurrentUser();

    expect(fetchCurrentUser).not.toHaveBeenCalled();
  });

  it("should set hasLoadedInitial even when /users/me fails", async () => {
    useAuthStore.setState({
      accessToken: "access-token",
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      hasLoadedInitial: false,
    });
    (fetchCurrentUser as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      response: { status: 401, data: { message: "Unauthorized" } },
    });

    await useAuthStore.getState().loadCurrentUser();

    const state = useAuthStore.getState();
    expect(state.hasLoadedInitial).toBe(true);
    expect(state.user).toBeNull();
  });
});
