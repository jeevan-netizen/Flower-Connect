import { create } from "zustand";
import type { AuthResponse, UserResponse } from "@/features/auth/types";
import type { FarmerRegisterResponse } from "@/features/farmer/types";
import {
  fetchCurrentUser,
  login as loginApi,
  logout as logoutApi,
  refresh as refreshApi,
  register as registerApi,
  registerFarmer as registerFarmerApi,
} from "../api";

interface AuthState {
  accessToken: string | null;
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  hasLoadedInitial: boolean;
  // true only for the page load in which the user just signed in through the
  // login form. RequireUnauth uses it to let LoginPage complete its own
  // role-based redirect (or show its refusal error) instead of unmounting the
  // form and sending the user to "/".
  justLoggedIn: boolean;

  setAuth: (auth: AuthResponse) => void;
  setError: (error: string | null) => void;
  setHasLoadedInitial: (value: boolean) => void;
  setJustLoggedIn: (value: boolean) => void;
  logout: () => void;

  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, phone: string | null, password: string) => Promise<void>;
  registerFarmer: (fullName: string, email: string, phone: string | null, password: string, address?: string) => Promise<FarmerRegisterResponse>;
  refresh: () => Promise<boolean>;
  loadCurrentUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
    (set, get) => ({
      accessToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
      hasLoadedInitial: false,
      justLoggedIn: false,

      setAuth: (auth: AuthResponse) => {
        set({
          accessToken: auth.accessToken,
          isAuthenticated: true,
          error: null,
        });
      },

      setJustLoggedIn: (value: boolean) => set({ justLoggedIn: value }),
      setError: (error: string | null) => set({ error }),
      setHasLoadedInitial: (value: boolean) => set({ hasLoadedInitial: value }),

      logout: () => {
        void logoutApi().catch(() => {});
        set({
          accessToken: null,
          user: null,
          isAuthenticated: false,
          justLoggedIn: false,
          error: null,
        });
      },

      login: async (email: string, password: string) => {
        set({ isLoading: true, error: null });
        try {
          const auth = await loginApi({ email, password });
          get().setAuth(auth);
          get().setJustLoggedIn(true);
          await get().loadCurrentUser();
        } catch (e) {
          const message = extractErrorMessage(e);
          set({ error: message, isAuthenticated: false, accessToken: null, user: null });
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
          set({ error: message, isAuthenticated: false, accessToken: null, user: null });
        } finally {
          set({ isLoading: false });
        }
      },

      registerFarmer: async (
        fullName: string,
        email: string,
        phone: string | null,
        password: string,
        address?: string,
      ) => {
        set({ isLoading: true, error: null });
        try {
          const response = await registerFarmerApi({ email, password, fullName, phone, address });
          // Farmer registration returns the created profile, not tokens.
          // Navigate to login with the email prefilled for secure sign-in handoff.
          // The actual navigation is handled by the page component.
          return response;
        } catch (e) {
          const message = extractErrorMessage(e);
          set({ error: message, isAuthenticated: false, accessToken: null, user: null });
          throw e;
        } finally {
          set({ isLoading: false });
        }
      },

      refresh: async () => {
        const { accessToken } = get();
        if (!accessToken) {
          set({ user: null, isAuthenticated: false });
          return false;
        }
        try {
          const auth = await refreshApi();
          get().setAuth(auth);
          await get().loadCurrentUser();
          return true;
        } catch (e) {
          set({
            accessToken: null,
            user: null,
            isAuthenticated: false,
            error: "Session expired. Please log in again.",
          });
          return false;
        }
      },

      loadCurrentUser: async () => {
        const { accessToken } = get();
        if (!accessToken) {
          set({ hasLoadedInitial: true });
          return;
        }
        try {
          const user = await fetchCurrentUser();
          set({ user, isAuthenticated: true, hasLoadedInitial: true });
        } catch (e) {
          set({ user: null, hasLoadedInitial: true });
        }
      },
    }),
  );

function extractErrorMessage(e: unknown): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string };
  return err?.response?.data?.message ?? err?.message ?? "An unexpected error occurred";
}