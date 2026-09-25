import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import type { AuthResponse } from "@/features/auth/types";

declare module "axios" {
  interface InternalAxiosRequestConfig {
    __isRetry?: boolean;
  }
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1",
  timeout: 15000,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

const PUBLIC_ENDPOINTS = ["/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"];

let isRefreshing = false;
let refreshPromise: Promise<AuthResponse | null> | null = null;

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const { accessToken } = useAuthStore.getState();
  const isPublic = PUBLIC_ENDPOINTS.some((endpoint) => config.url?.startsWith(endpoint));
  if (accessToken && !isPublic) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

async function attemptRefresh(): Promise<string | null> {
  const { setAuth } = useAuthStore.getState();

  if (isRefreshing && refreshPromise) {
    return refreshPromise.then((auth) => auth?.accessToken ?? null);
  }

  isRefreshing = true;
  refreshPromise = api
    .post<AuthResponse>("/auth/refresh")
    .then((response) => {
      const auth = response.data;
      setAuth(auth);
      return auth;
    })
    .catch(() => {
      useAuthStore.getState().logout();
      return null;
    })
    .finally(() => {
      isRefreshing = false;
      refreshPromise = null;
    });

  return refreshPromise.then((auth) => auth?.accessToken ?? null);
}

api.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const { config } = error;
    if (error.response?.status !== 401 || !config || config.__isRetry) {
      return Promise.reject(error);
    }

    const isPublic = PUBLIC_ENDPOINTS.some((endpoint) => config.url?.startsWith(endpoint));
    if (isPublic) {
      return Promise.reject(error);
    }

    config.__isRetry = true;
    const newToken = await attemptRefresh();

    if (newToken) {
      config.headers.Authorization = `Bearer ${newToken}`;
      return api(config);
    }

    return Promise.reject(error);
  },
);

export default api;
export type { AxiosError };
