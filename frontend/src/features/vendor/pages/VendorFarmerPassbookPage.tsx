import { useParams } from "react-router-dom";
import { useVendorPassbook, useVendorCollections, useConfirmCollection, useRejectCollection } from "@/features/farmer/queries";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { DepositForm } from "@/features/farmer/components/DepositForm";
import { formatMoney } from "@/features/vendor/format";
import { formatDateTime } from "@/features/vendor/format";
import { AnimatedList } from "@/motion/AnimatedList";

/**
 * Vendor's passbook for one farmer link: link summary, outstanding balance, an
 * "Add deposit" form, and the pending-collection queue with confirm/reject
 * actions.
 */
export function VendorFarmerPassbookPage() {
  const { linkId } = useParams<{ linkId: string }>();
  const id = Number(linkId);
  const { data: passbook, isPending, error, refetch } = useVendorPassbook(
    Number.isNaN(id) ? null : id,
  );

  const { data: collections, isPending: pendingCollections } = useVendorCollections("PENDING");
  const confirmCollection = useConfirmCollection();
  const rejectCollection = useRejectCollection();

  if (isPending || pendingCollections) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading passbook…
      </div>
    );
  }

  if (error || !passbook) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  const pendingForThisLink = (collections ?? []).filter((c) => c.linkId === passbook.linkId);

  return (
    <div className="space-y-6">
      <PageHeading
        title={`Passbook — ${passbook.farmerName}`}
        description={`Link with ${passbook.vendorName} • ${passbook.linkStatus}`}
      />

      <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          label="Outstanding balance"
          value={formatMoney(passbook.outstandingBalance)}
          hint="Deposits minus confirmed collections"
        />
        <StatCard
          label="Link status"
          value={passbook.linkStatus}
        />
        <StatCard
          label="Entries"
          value={String(passbook.entries.length)}
          hint="Deposits and collections, newest first"
        />
      </AnimatedList>

      <Card title="Record a flower deposit">
        <DepositForm linkId={passbook.linkId} onSuccess={() => void refetch()} />
      </Card>

      {pendingForThisLink.length > 0 && (
        <Card title="Pending collections">
          <table className="w-full text-sm divide-y divide-slate-200">
            <thead>
              <tr>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Amount</th>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Mode</th>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Requested</th>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Note</th>
                <th className="text-right font-semibold text-slate-600 px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingForThisLink.map((collection) => (
                <tr key={collection.id} className="hover:bg-slate-50/50">
                  <td className="px-3 py-2 font-mono text-slate-900">
                    {formatMoney(collection.amount)}
                  </td>
                  <td className="px-3 py-2 text-slate-700">{collection.mode}</td>
                  <td className="px-3 py-2 text-slate-700">
                    {formatDateTime(collection.requestedAt)}
                  </td>
                  <td className="px-3 py-2 text-slate-700">
                    {collection.note ?? "—"}
                  </td>
                  <td className="text-right px-3 py-2">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        disabled={confirmCollection.isPending}
                        onClick={() => void confirmCollection.mutate(collection.id)}
                        className="rounded-md border border-transparent bg-brand-600 py-1 px-2 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                      >
                        Confirm
                      </button>
                      <button
                        type="button"
                        disabled={rejectCollection.isPending}
                        onClick={() => void rejectCollection.mutate(collection.id)}
                        className="rounded-md border border-slate-300 bg-white py-1 px-2 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                      >
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <div className="mt-4 flex justify-end">
        <a
          href="/vendor/farmers"
          className="text-sm font-medium text-brand-700 hover:text-brand-900"
        >
          Back to farmers
        </a>
      </div>
    </div>
  );
}
