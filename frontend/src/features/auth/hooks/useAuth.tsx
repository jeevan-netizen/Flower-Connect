import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";

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

  useEffect(() => {
    if (accessToken && !hasLoadedInitial) {
      void loadCurrentUser();
    }
  }, [accessToken, hasLoadedInitial, loadCurrentUser]);

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
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
      </div>
    );
  }

  return <>{children}</>;
}

export function RequireUnauth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, hasLoadedInitial } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (hasLoadedInitial && isAuthenticated) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, hasLoadedInitial, navigate]);

  if (!hasLoadedInitial || isAuthenticated) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
      </div>
    );
  }

  return <>{children}</>;
}
