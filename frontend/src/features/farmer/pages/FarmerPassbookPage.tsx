import { useParams } from "react-router-dom";
import { Link } from "react-router-dom";
import { useFarmerPassbook } from "@/features/farmer/queries";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { CollectionForm } from "@/features/farmer/components/CollectionForm";
import { formatMoney } from "@/features/vendor/format";
import { formatLinkStatus, LINK_STATUS_TONES } from "@/features/farmer/types";
import { formatDateTime } from "@/features/vendor/format";
import { AnimatedList } from "@/motion/AnimatedList";

/**
 * Farmer's passbook for one link: link summary, outstanding balance, and a
 * combined deposit/collection entry list with running balance.
 */
export function FarmerPassbookPage() {
  const { linkId } = useParams<{ linkId: string }>();
  const id = Number(linkId);
  const { data: passbook, isPending, error, refetch } = useFarmerPassbook(
    Number.isNaN(id) ? null : id,
  );

  if (isPending) {
    return (
      <div className="rounded-lg border bolder-line bg-bolder-surface p-6 text-sm text-bolder-muted">
        Loading passbook…
      </div>
    );
  }

  if (error || !passbook) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title={`Passbook — ${passbook.vendorName}`}
        description={`Link with ${passbook.farmerName} • ${formatLinkStatus(passbook.linkStatus)}`}
      />

      <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          label="Outstanding balance"
          value={formatMoney(passbook.outstandingBalance)}
          hint="Deposits minus confirmed collections"
        />
        <StatCard
          label="Link status"
          value={
            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${LINK_STATUS_TONES[passbook.linkStatus]}`}>
              {passbook.linkStatus}
            </span>
          }
        />
        <StatCard
          label="Entries"
          value={String(passbook.entries.length)}
          hint="Deposits and collections, newest first"
        />
      </AnimatedList>

      <Card title="Request a collection">
        {passbook.linkStatus !== "ACTIVE" && (
        <p className="mb-3 rounded-md bg-bolder-warning-surface p-3 text-sm text-bolder-warning">
          This link is {passbook.linkStatus.toLowerCase()}. New collection requests are blocked while the link is not active, though your passbook remains visible.
        </p>
        )}
        <CollectionForm
          linkId={passbook.linkId}
          disabled={passbook.linkStatus !== "ACTIVE"}
          onSuccess={() => void refetch()}
        />
      </Card>

      <Card title="Ledger entries">
        {passbook.entries.length === 0 ? (
          <p className="text-sm text-bolder-muted">
            No deposits or collections yet for this link.
          </p>
        ) : (
          <table className="w-full text-sm divide-y divide-bolder-line">
            <thead>
              <tr>
                <th className="text-left font-semibold text-bolder-text px-3 py-2">Date</th>
                <th className="text-left font-semibold text-bolder-text px-3 py-2">Entry</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Gross</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Deduction</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Net</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Rate</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Amount</th>
                <th className="text-right font-semibold text-bolder-text px-3 py-2">Running balance</th>
              </tr>
            </thead>
            <tbody>
              {passbook.entries.map((entry) => (
                <tr key={entry.id} className="hover:bg-bolder-surface-muted">
                  <td className="px-3 py-2 text-bolder-text">
                    {formatDateTime(entry.date)}
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                          entry.type === "DEPOSIT"
                            ? "bg-bolder-blush-light text-bolder-rose-deep"
                            : entry.status === "CONFIRMED"
                              ? "bg-bolder-blush-light text-bolder-rose-deep"
                              : entry.status === "REJECTED"
                                ? "bg-bolder-bg text-bolder-text"
                                : "bg-bolder-warning-surface text-bolder-warning"
                        }`}
                      >
                        {entry.type === "DEPOSIT" ? "Deposit" : entry.status ?? "—"}
                      </span>
                      <span className="text-bolder-muted">
                        {entry.type === "DEPOSIT"
                          ? entry.flowerType ?? "Flower deposit"
                          : `Money collection ${entry.mode ?? ""}`}
                      </span>
                    </div>
                  </td>
                  <td className="text-right px-3 py-2 text-bolder-text">
                    {entry.type === "DEPOSIT" && entry.grossKg != null ? `${entry.grossKg.toFixed(2)} kg` : "—"}
                  </td>
                  <td className="text-right px-3 py-2 text-bolder-text">
                    {entry.type === "DEPOSIT" && entry.deductionKg != null ? `${entry.deductionKg.toFixed(2)} kg` : "—"}
                  </td>
                  <td className="text-right px-3 py-2 text-bolder-text">
                    {entry.type === "DEPOSIT" && entry.netKg != null ? `${entry.netKg.toFixed(2)} kg` : "—"}
                  </td>
                  <td className="text-right px-3 py-2 text-bolder-text">
                    {entry.type === "DEPOSIT" && entry.pricePerKg != null ? `${formatMoney(entry.pricePerKg)}/kg` : "—"}
                  </td>
                  <td className="text-right px-3 py-2">
                    {entry.type === "DEPOSIT" ? "+" : "-"}
                    {formatMoney(entry.amount ?? 0)}
                  </td>
                  <td className="text-right px-3 py-2 font-mono text-bolder-text">
                    {formatMoney(entry.runningBalance)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <div className="mt-4 flex justify-end">
        <Link
          to="/farmer"
          className="text-base font-medium text-bolder-rose-strong hover:text-bolder-rose-deep"
        >
          Back to dashboard
        </Link>
      </div>
    </div>
  );
}
