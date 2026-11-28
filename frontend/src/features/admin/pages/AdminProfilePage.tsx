import { Link, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearAdminCache } from "@/features/admin/queries";
import { useQueryClient } from "@tanstack/react-query";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { ReadOnlyRow } from "@/features/vendor/components/FormFields";
import { PRESSABLE } from "@/motion/pressable";

/**
 * Admin-facing view of the shared `/profile` destination.
 *
 * Renders only account data that comes from the authenticated user's own JWT
 * subject (name, email, role, member-since). No passwords, tokens, or secrets
 * are ever displayed or stored as profile data.
 *
 * Actions:
 * - Change password links to the existing `/settings/password` route, which the
 *   router already guards for `CUSTOMER`, `VENDOR` and `ADMIN` alike and which
 *   calls `POST /api/v1/users/me/password` — an endpoint valid for admins, so
 *   no new endpoint is invented.
 * - Log out clears the admin query cache, ends the auth session, and navigates
 *   home, mirroring the customer and vendor Profile logout pattern.
 */
export function AdminProfilePage() {
  const { user, logout } = useAuthStore();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const handleLogout = () => {
    clearAdminCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Admin profile"
        description="Your platform administrator account details."
      />

      {/* -------------------------------- Account section -------------------------------- */}
      <Card>
        <div className="space-y-5">
          <PageHeading
            title="Account"
            description="Your name and email are your account identity. Role and member-since are read-only."
          />

          <dl className="space-y-3 pt-2 border-t border-bolder-line">
            <ReadOnlyRow label="Full name" value={user?.fullName ?? "—"} />
            <ReadOnlyRow label="Email" value={user?.email ?? "—"} />
            <ReadOnlyRow label="Role" value={user?.role ?? "—"} />
            <ReadOnlyRow label="Admin id" value={user?.id?.toString() ?? "—"} />
            <ReadOnlyRow label="Member since" value={user?.createdAt ?? "—"} />
          </dl>

          <p className="text-sm text-bolder-muted">
            Want to change your password?{" "}
            <Link
              to="/settings/password"
              className={`underline underline-offset-2 hover:text-bolder-blush ${PRESSABLE}`}
            >
              Change password
            </Link>
          </p>
        </div>
      </Card>

      {/* ----------------------------------- Logout ----------------------------------- */}
      <button
        type="button"
        onClick={handleLogout}
        className={`w-full inline-flex justify-center rounded-md border border-transparent bg-bolder-rose px-4 py-2.5 text-sm font-medium text-bolder-bg hover:bg-bolder-rose-strong focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 ${PRESSABLE}`}
      >
        Log out
      </button>
    </div>
  );
}
