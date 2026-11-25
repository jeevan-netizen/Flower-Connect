import { Link } from "react-router-dom";
import { useFarmerLinks } from "@/features/farmer/queries";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { formatLinkStatus, LINK_STATUS_TONES } from "@/features/farmer/types";
import { formatMoney } from "@/features/vendor/format";
import { AnimatedList } from "@/motion/AnimatedList";

/**
 * Farmer dashboard (Stage 3 of the farmer module).
 *
 * Lists the vendors this farmer is linked to, each link's status and the
 * outstanding balance the farmer is owed across active links. The balance is the
 * backend's own `sum(deposits) - sum(CONFIRMED collections)` per link, so the
 * browser never recomputes it.
 */
export function FarmerDashboardPage() {
  const { data: links, isPending, error, refetch } = useFarmerLinks();

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your links…
      </div>
    );
  }

  if (error) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  const outstanding = (links ?? []).reduce(
    (total, link) => total + Number(link.outstandingBalance ?? 0),
    0,
  );

  return (
    <div className="space-y-6">
      <PageHeading
        title="My florists"
        description="Vendors you are linked to and what they owe you."
      />

      <StatCard
        label="Linked vendors"
        value={String(links?.length ?? 0)}
        hint={links?.some((l) => l.status === "PENDING") ? "A request is awaiting a vendor decision" : "All links decided"}
      />
      <StatCard
        label="Outstanding balance"
        value={formatMoney(outstanding)}
        hint="Deposits minus confirmed collections"
      />

      <Card title="Your links">
        {(links?.length ?? 0) === 0 ? (
          <p className="text-sm text-slate-600">
            You have no linked florists yet. Browse vendors and send a join request
            to start receiving deposits and collections.
          </p>
        ) : (
          <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {(links ?? []).map((link) => (
              <Link
                key={link.id}
                to={`/farmer/links/${link.id}/passbook`}
                className="group rounded-lg border border-slate-200 bg-white p-4 hover:border-brand-300/60 transition-colors"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="font-semibold text-slate-900">{link.vendorName}</h3>
                    <p className="mt-0.5 text-sm text-slate-600">
                      {formatLinkStatus(link.status)}
                    </p>
                  </div>
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      LINK_STATUS_TONES[link.status]
                    }`}
                  >
                    {link.status}
                  </span>
                </div>
                <div className="mt-3 flex items-center justify-between">
                  <span className="text-sm font-medium text-slate-900">
                    Outstanding {formatMoney(Number(link.outstandingBalance ?? 0))}
                  </span>
                  <Link
                    to={`/farmer/links/${link.id}/passbook`}
                    className="text-sm font-medium text-brand-700 hover:text-brand-900"
                  >
                    Open passbook
                  </Link>
                </div>
              </Link>
            ))}
          </AnimatedList>
        )}
      </Card>

      <Card title="Join a florist">
        <p className="text-sm text-slate-700">
          Send a join request to any approved florist. Once they accept, you are
          linked and they can record flower deposits against you.
        </p>
        <Link to="/farmer/vendors" className="mt-3 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
          Browse vendors →
        </Link>
      </Card>
    </div>
  );
}
