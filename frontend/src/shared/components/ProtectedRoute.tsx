import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";

interface ProtectedRouteProps {
  /**
   * Roles allowed through, using the seeded role names. Empty/omitted means
   * "any authenticated user" — authentication only.
   *
   * This is UX gating for routing and navigation. The backend remains the
   * security authority and re-checks both the role and (for vendor features) the
   * approval state on every request.
   */
  roles?: string[];
  children: React.ReactNode;
}

function FullPageSpinner() {
  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-600"></div>
    </div>
  );
}

/**
 * `<ProtectedRoute roles={[...]} />` — the route guard named in
 * `docs/rules.md` §8.5. It reads the single persisted auth store and never
 * keeps its own copy of the session.
 *
 * Three outcomes:
 *   - session not restored yet → spinner (the "initial load finished" flag is
 *     set on success *and* failure, so this cannot hang);
 *   - not authenticated → redirect to `/login`, remembering where the user was;
 *   - authenticated but wrong role → redirect home, because the user signed in
 *     successfully and there is nothing to recover from here.
 */
export function ProtectedRoute({ roles, children }: ProtectedRouteProps) {
  const { isAuthenticated, hasLoadedInitial, user } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (hasLoadedInitial && !isAuthenticated) {
      navigate("/login", { replace: true, state: { from: location.pathname } });
    }
  }, [hasLoadedInitial, isAuthenticated, location.pathname, navigate]);

  if (!hasLoadedInitial || !isAuthenticated) {
    return <FullPageSpinner />;
  }

  if (roles && roles.length > 0 && (!user || !roles.includes(user.role))) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
        <div className="max-w-md rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h1 className="text-lg font-semibold text-slate-900">Not available for your account</h1>
          <p className="mt-2 text-sm text-slate-600">
            Your account does not have access to this area.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
