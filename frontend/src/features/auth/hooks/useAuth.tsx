import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { FlowerLoader } from "@/motion/FlowerLoader";

export function useAuth() {
  return useAuthStore();
}

export function useRequireAuth() {
  const { isAuthenticated, isLoading, user } = useAuthStore();
  return { isAuthenticated, isLoading, user };
}

export function useInitAuth() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const hasLoadedInitial = useAuthStore((state) => state.hasLoadedInitial);
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser);
  const setHasLoadedInitial = useAuthStore((state) => state.setHasLoadedInitial);
  const refresh = useAuthStore((state) => state.refresh);
  const setError = useAuthStore((state) => state.setError);

  useEffect(() => {
    if (hasLoadedInitial) return;
    if (accessToken) {
      void loadCurrentUser();
    } else {
      // No access token in memory. Attempt to restore the session from the
      // HttpOnly refresh cookie via the refresh endpoint. If refresh fails
      // (no cookie, expired, or invalid), the session is genuinely over.
      void refresh().catch(() => setError(null)).finally(() => setHasLoadedInitial(true));
    }
  }, [accessToken, hasLoadedInitial, loadCurrentUser, refresh, setHasLoadedInitial, setError]);

  return { hasLoadedInitial };
}

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, hasLoadedInitial } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (hasLoadedInitial && !isAuthenticated) {
      navigate("/login", { replace: true });
    }
  }, [isAuthenticated, hasLoadedInitial, navigate]);

  if (!hasLoadedInitial || !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Checking your session" />
      </div>
    );
  }

  return <>{children}</>;
}

export function RequireUnauth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, hasLoadedInitial, justLoggedIn } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    // Only redirect a visitor who arrived with an already-valid session (page
    // load). A user who just signed in through this form is handled by
    // LoginPage itself (role-based redirect or inline refusal). justLoggedIn is
    // read live so the guard does not redirect while a login is in progress;
    // LoginPage resets it after completing the navigation, so a later revisit
    // to /login by an authenticated user is redirected home instead of being
    // shown the login form again.
    if (hasLoadedInitial && isAuthenticated && !justLoggedIn) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, hasLoadedInitial, justLoggedIn, navigate]);

  // Session not restored yet → loader. A just-logged-in user stays on this
  // page so LoginPage can finish its own navigation or show its error; an
  // unauthenticated user sees the form. Only a session-restored user is shown
  // a loader while the guard sends them home.
  if (!hasLoadedInitial) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Checking your session" />
      </div>
    );
  }
  if (isAuthenticated && !justLoggedIn) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Checking your session" />
      </div>
    );
  }

  return <>{children}</>;
}
