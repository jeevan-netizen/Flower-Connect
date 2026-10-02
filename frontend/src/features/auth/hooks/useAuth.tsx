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

  useEffect(() => {
    if (hasLoadedInitial) return;
    if (accessToken) {
      void loadCurrentUser();
    } else {
      setHasLoadedInitial(true);
    }
  }, [accessToken, hasLoadedInitial, loadCurrentUser, setHasLoadedInitial]);

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
  const { isAuthenticated, hasLoadedInitial } = useAuthStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (hasLoadedInitial && isAuthenticated) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, hasLoadedInitial, navigate]);

  if (!hasLoadedInitial || isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Checking your session" />
      </div>
    );
  }

  return <>{children}</>;
}
