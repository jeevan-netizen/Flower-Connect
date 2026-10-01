import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from "vitest";
import { type AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";

interface MockAuthState {
  accessToken: string | null;
  user: unknown;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  hasLoadedInitial: boolean;
  setAuth: Mock;
  setError: Mock;
  logout: Mock;
  login: Mock;
  register: Mock;
  refresh: Mock;
  loadCurrentUser: Mock;
}

const mockGetState = vi.hoisted(() => vi.fn() as Mock);
const mockSetAuth = vi.fn();
const mockLogout = vi.fn();

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: {
    getState: mockGetState,
  },
}));

function defaultState(): MockAuthState {
  return {
    accessToken: "test-access-token",
    user: null,
    isAuthenticated: true,
    isLoading: false,
    error: null,
    hasLoadedInitial: true,
    setAuth: mockSetAuth,
    setError: vi.fn(),
    logout: mockLogout,
    login: vi.fn(),
    register: vi.fn(),
    refresh: vi.fn(),
    loadCurrentUser: vi.fn(),
  };
}

function make401Error(config: InternalAxiosRequestConfig): AxiosError {
  return {
    isAxiosError: true,
    name: "AxiosError",
    code: "ERR_BAD_REQUEST",
    config,
    request: {},
    response: {
      status: 401,
      statusText: "Unauthorized",
      headers: {},
      data: { message: "Unauthorized" },
      config,
    },
    message: "Request failed with status code 401",
    toJSON: () => ({}),
  } as AxiosError;
}

function makeNon401Error(config: InternalAxiosRequestConfig): AxiosError {
  return {
    isAxiosError: true,
    name: "AxiosError",
    code: "ERR_BAD_REQUEST",
    config,
    request: {},
    response: {
      status: 403,
      statusText: "Forbidden",
      headers: {},
      data: { message: "Forbidden" },
      config,
    },
    message: "Request failed with status code 403",
    toJSON: () => ({}),
  } as AxiosError;
}

describe("Axios API Interceptor", () => {
  let api: typeof import("@/shared/lib/api").default;

  beforeEach(async () => {
    vi.resetModules();
    vi.clearAllMocks();

    mockGetState.mockReturnValue(defaultState());

    api = (await import("@/shared/lib/api")).default;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("should attach access token to authenticated requests", async () => {
    const adapter = vi.fn().mockResolvedValue({
      data: {},
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    await api.get("/users/me");

    const config = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    expect(config.headers.Authorization).toBe("Bearer test-access-token");
  });

  it("should not attach auth headers to public endpoints", async () => {
    const adapter = vi.fn().mockResolvedValue({
      data: {},
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    await api.post("/auth/login", { email: "test@test.com", password: "password" });

    const config = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    expect(config.headers.Authorization).toBeUndefined();
  });

  it("should attach client header to cookie-authenticated endpoints", async () => {
    const adapter = vi.fn().mockResolvedValue({
      data: {},
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    await api.post("/auth/refresh");
    await api.post("/auth/logout");

    const refreshConfig = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    const logoutConfig = adapter.mock.calls[1][0] as InternalAxiosRequestConfig;
    expect(refreshConfig.headers["X-FlowerConnect-Client"]).toBe("1");
    expect(logoutConfig.headers["X-FlowerConnect-Client"]).toBe("1");
  });

  it("should trigger token refresh on 401 and retry request", async () => {
    const retryResponse = { id: 1, email: "user@test.com" };

    const callLog: string[] = [];
    const adapter = vi.fn().mockImplementation((config: InternalAxiosRequestConfig) => {
      if (config.url?.startsWith("/auth/refresh")) {
        callLog.push("refresh");
        return Promise.resolve({
          data: {
            accessToken: "new-access-token",
            refreshToken: "new-refresh-token",
            tokenType: "Bearer",
            expiresIn: 900000,
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config,
        });
      }
      callLog.push("user");
      if (callLog.filter((x) => x === "user").length === 1) {
        return Promise.reject(make401Error(config));
      }
      return Promise.resolve({
        data: retryResponse,
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      });
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    const result = await api.get("/users/me");

    expect(result.data).toEqual(retryResponse);
    expect(result.data.email).toBe("user@test.com");
    expect(callLog.filter((x) => x === "refresh").length).toBe(1);
    expect(callLog.filter((x) => x === "user").length).toBe(2);
  });

  it("should share one refresh request for concurrent 401s", async () => {
    const refreshResponse = {
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      tokenType: "Bearer",
      expiresIn: 900000,
    };

    let refreshCallCount = 0;
    let userCallCount = 0;

    const adapter = vi.fn().mockImplementation((config: InternalAxiosRequestConfig) => {
      if (config.url?.startsWith("/auth/refresh")) {
        refreshCallCount++;
        return Promise.resolve({
          data: refreshResponse,
          status: 200,
          statusText: "OK",
          headers: {},
          config,
        });
      }
      userCallCount++;
      return Promise.reject(make401Error(config));
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    const p1 = api.get("/users/me").catch(() => null);
    const p2 = api.get("/users/me").catch(() => null);

    await Promise.all([p1, p2]);

    expect(refreshCallCount).toBe(1);
    expect(userCallCount).toBe(4);
  });

  it("should call logout when refresh fails", async () => {
    const adapter = vi.fn().mockImplementation((config: InternalAxiosRequestConfig) => {
      return Promise.reject(make401Error(config));
    });

    api.defaults.adapter = adapter as AxiosAdapter;

    await api.get("/users/me").catch(() => null);

    expect(mockLogout).toHaveBeenCalled();
  });

  it("should not retry non-401 errors", async () => {
    const adapter = vi.fn().mockRejectedValue(makeNon401Error({} as InternalAxiosRequestConfig));

    api.defaults.adapter = adapter as AxiosAdapter;

    const error = (await api.get("/users/me").catch((e: unknown) => e)) as AxiosError;

    expect(error.response?.status).toBe(403);
    expect(adapter).toHaveBeenCalledTimes(1);
  });
});
