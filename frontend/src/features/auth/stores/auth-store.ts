import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthResponse, UserResponse } from "@/features/auth/types";
import {
  fetchCurrentUser,
  login as loginApi,
  logout as logoutApi,
  refresh as refreshApi,
  register as registerApi,
} from "../api";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  hasLoadedInitial: boolean;

  setAuth: (auth: AuthResponse) => void;
  setError: (error: string | null) => void;
  logout: () => void;

  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, phone: string | null, password: string) => Promise<void>;
  refresh: () => Promise<boolean>;
  loadCurrentUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      hasLoadedInitial: false,

      setAuth: (auth: AuthResponse) => {
        set({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          isAuthenticated: true,
          error: null,
        });
      },

      setError: (error: string | null) => set({ error }),

      logout: () => {
        const { refreshToken } = get();
        if (refreshToken) {
          void logoutApi({ refreshToken }).catch(() => {});
        }
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false,
          error: null,
        });
      },

      login: async (email: string, password: string) => {
        set({ isLoading: true, error: null });
        try {
          const auth = await loginApi({ email, password });
          get().setAuth(auth);
          await get().loadCurrentUser();
        } catch (e) {
          const message = extractErrorMessage(e);
          set({ error: message, isAuthenticated: false, accessToken: null, refreshToken: null, user: null });
        } finally {
          set({ isLoading: false });
        }
      },

      register: async (fullName: string, email: string, phone: string | null, password: string) => {
        set({ isLoading: true, error: null });
        try {
          const auth = await registerApi({ email, password, fullName, phone });
          get().setAuth(auth);
          await get().loadCurrentUser();
        } catch (e) {
          const message = extractErrorMessage(e);
          set({ error: message, isAuthenticated: false, accessToken: null, refreshToken: null, user: null });
        } finally {
          set({ isLoading: false });
        }
      },

      refresh: async () => {
        const { refreshToken } = get();
        if (!refreshToken) {
          set({ accessToken: null, user: null, isAuthenticated: false });
          return false;
        }
        try {
          const auth = await refreshApi({ refreshToken });
          get().setAuth(auth);
          await get().loadCurrentUser();
          return true;
        } catch (e) {
          set({
            accessToken: null,
            refreshToken: null,
            user: null,
            isAuthenticated: false,
            error: "Session expired. Please log in again.",
          });
          return false;
        }
      },

      loadCurrentUser: async () => {
        const { accessToken } = get();
        if (!accessToken) return;
        try {
          const user = await fetchCurrentUser();
          set({ user, isAuthenticated: true, hasLoadedInitial: true });
        } catch (e) {
          set({ user: null, hasLoadedInitial: true });
        }
      },
    }),
    {
      name: "fc-auth",
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);

function extractErrorMessage(e: unknown): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string };
  return err?.response?.data?.message ?? err?.message ?? "An unexpected error occurred";
}
