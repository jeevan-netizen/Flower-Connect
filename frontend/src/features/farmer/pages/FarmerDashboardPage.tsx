import { Link } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import { useFarmerLinks, farmerKeys } from "@/features/farmer/queries";
import { fetchFarmerPassbook } from "@/features/farmer/api";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { formatMoney } from "@/features/vendor/format";

/**
 * Farmer dashboard (Stage 3 of the farmer module).
 *
 * Shows compact live balance and pending collection summaries. Per-vendor links
 * and records are available from My Ledger instead of being repeated here.
 */
export function FarmerDashboardPage() {
  const { data: links, isPending: linksPending, error: linksError, refetch: refetchLinks } =
    useFarmerLinks();

  // One passbook query per link so the overview cards can surface pending
  // collections without the browser inventing a balance.
  const passbookResults = useQueries({
    queries: (links ?? []).map((link) => ({
      queryKey: farmerKeys.passbook(link.id),
      queryFn: () => fetchFarmerPassbook(link.id),
    })),
  });

  if (linksPending) {
    return (
      <div className="rounded-lg border bolder-line bg-bolder-surface p-6 text-sm text-bolder-muted">
        Loading your links…
      </div>
    );
  }

  if (linksError) {
    return <FarmerErrorState error={linksError} onRetry={() => void refetchLinks()} />;
  }

  const outstanding = (links ?? []).reduce(
    (total, link) => total + Number(link.outstandingBalance ?? 0),
    0,
  );

  const pendingCollections = passbookResults.reduce((total, result) => {
    const passbook = result.data;
    if (!passbook) return total;
    return (
      total +
      passbook.entries.reduce(
        (sum, entry) =>
          entry.type === "COLLECTION" && entry.status === "PENDING"
            ? sum + Number(entry.amount ?? 0)
            : sum,
        0,
      )
    );
  }, 0);

  return (
    <div className="space-y-6">
      <PageHeading
        title="Overview"
        description="Your linked florists and what they owe you."
      />

      <StatCard
        label="Current balance"
        value={formatMoney(outstanding)}
        hint="Deposits minus confirmed collections"
      />
      <StatCard
        label="Pending collections"
        value={formatMoney(pendingCollections)}
        hint="Collections requested but not yet confirmed"
      />
      <StatCard
        label="Linked vendors"
        value={String(links?.length ?? 0)}
        hint={links?.some((l) => l.status === "PENDING") ? "A request is awaiting a vendor decision" : "All links decided"}
      />

      <Card title="Join a florist">
        <p className="text-sm text-bolder-muted">
          Send a join request to any approved florist. Once they accept, you are
          linked and they can record flower deposits against you.
        </p>
        <Link to="/farmer/vendors" className="mt-3 inline-block text-base font-medium text-bolder-rose-strong hover:text-bolder-rose-deep">
          Browse vendors →
        </Link>
      </Card>
    </div>
  );
}
