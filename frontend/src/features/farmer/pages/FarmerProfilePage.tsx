import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useFarmerProfile, clearFarmerCache } from "@/features/farmer/queries";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { AnimatedList } from "@/motion/AnimatedList";
import { PRESSABLE, FOCUS_RING } from "@/motion/pressable";

/**
 * Farmer profile: the signed-in farmer's own name, mobile number and address,
 * plus a Log out button that clears the farmer cache and signs out consistently
 * with the rest of the app. Data comes from `GET /api/v1/farmer/profile`, which
 * is scoped to the authenticated farmer — nothing is invented or hardcoded.
 */
export function FarmerProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const { data: profile, isPending, error, refetch } = useFarmerProfile();

  const handleLogout = () => {
    clearFarmerCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your profile…
      </div>
    );
  }

  if (error || !profile) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6">
      <PageHeading title="My profile" description="Your FlowerConnect farmer account." />

      <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard label="Name" value={profile.fullName} />
        <StatCard label="Mobile" value={profile.phone ?? "Not provided"} />
        <StatCard label="Email" value={profile.email} />
        <StatCard label="Address" value={profile.address ?? "Not provided"} />
      </AnimatedList>

      <Card title="Account">
        <dl className="divide-y divide-slate-200 text-sm">
          <div className="grid grid-cols-2 gap-4 py-3 sm:py-4">
            <dt className="font-medium text-slate-600">Role</dt>
            <dd className="font-medium text-slate-900 capitalize">{profile.role}</dd>
            <dt className="font-medium text-slate-600">Account id</dt>
            <dd className="font-mono font-medium text-slate-900">{String(profile.id)}</dd>
          </div>
        </dl>
      </Card>

      <div className="mt-6">
        <button
          type="button"
          onClick={handleLogout}
          className={`w-full rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-bolder-rose focus-visible:ring-offset-2 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Log out
        </button>
      </div>
    </div>
  );
}
