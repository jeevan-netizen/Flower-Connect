import { Link } from "react-router-dom";
import { useFarmerLinks } from "@/features/farmer/queries";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { formatLinkStatus, LINK_STATUS_TONES } from "@/features/farmer/types";
import { formatMoney } from "@/features/vendor/format";
import { AnimatedList } from "@/motion/AnimatedList";

/**
 * Farmer's digital ledger: the farmer's linked vendors, each presented as its own
 * clearly separated passbook entry point. Records are grouped by vendor so a
 * farmer can see, at a glance, what each florist owes them and open that vendor's
 * full passbook (deposits, collections, running balance) from here.
 */
export function FarmerLedgerPage() {
  const { data: links, isPending, error, refetch } = useFarmerLinks();

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your ledger…
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
        title="My ledger"
        description="Your deposits and collections, separated by florist. Open a passbook to see the full entry history and running balance."
      />

      <StatCard
        label="Current balance"
        value={formatMoney(outstanding)}
        hint="Deposits minus confirmed collections, across all linked florists"
      />

      {(links?.length ?? 0) === 0 ? (
        <Card title="Your ledger is empty">
          <p className="text-sm text-slate-600">
            You have no linked florists yet. Browse vendors and send a join
            request to start receiving deposits and collections.
          </p>
          <Link
            to="/farmer/vendors"
            className="mt-3 inline-block text-sm font-medium text-brand-700 hover:text-brand-900"
          >
            Browse vendors →
          </Link>
        </Card>
      ) : (
        <Card title="Your florists">
          <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {(links ?? []).map((link) => (
              <div
                key={link.id}
                className="rounded-lg border border-slate-200 bg-white p-4"
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
                    Open passbook →
                  </Link>
                </div>
              </div>
            ))}
          </AnimatedList>
        </Card>
      )}
    </div>
  );
}
