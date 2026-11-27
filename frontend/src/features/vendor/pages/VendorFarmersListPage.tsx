import { Link } from "react-router-dom";
import { useVendorProfile } from "@/features/vendor/queries";
import { useVendorFarmerLinks, useAcceptLink, useRejectLink, useSuspendLink, useReactivateLink } from "@/features/farmer/queries";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { formatMoney } from "@/features/vendor/format";
import { LINK_STATUS_TONES } from "@/features/farmer/types";

/**
 * Vendor's farmer list: every farmer linked to this vendor, the link status and
 * the balance owed, plus the accept/reject/suspend/reactivate actions for each
 * link.
 */
export function VendorFarmersListPage() {
  const { data: profile, isPending, error, refetch } = useVendorProfile();
  const { data: links, isPending: pendingLinks } = useVendorFarmerLinks();

  const acceptLink = useAcceptLink();
  const rejectLink = useRejectLink();
  const suspendLink = useSuspendLink();
  const reactivateLink = useReactivateLink();

  if (isPending || pendingLinks) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading farmers…
      </div>
    );
  }

  if (error) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  const farmerLinks = links ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title="Linked farmers"
        description={`Farmers linked to ${profile?.businessName ?? "this vendor"}.`}
      />

      {profile?.status !== "APPROVED" && (
        <Card title="Approval required">
          <p className="text-sm text-slate-700">
            Approve your vendor profile before managing farmer links.
          </p>
        </Card>
      )}

      <Card title="Farmers">
        {farmerLinks.length === 0 ? (
          <p className="text-sm text-slate-600">
            No linked farmers yet. Approve a farmer's join request to link them.
          </p>
        ) : (
          <table className="w-full text-sm divide-y divide-slate-200">
            <thead>
              <tr>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Farmer</th>
                <th className="text-left font-semibold text-slate-600 px-3 py-2">Link</th>
                <th className="text-right font-semibold text-slate-600 px-3 py-2">Owed</th>
                <th className="text-right font-semibold text-slate-600 px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {farmerLinks.map((link) => {
                const isPending = link.status === "PENDING";
                const isActive = link.status === "ACTIVE";
                const isSuspended = link.status === "SUSPENDED";
                return (
                  <tr key={link.id} className="hover:bg-slate-50/50">
                    <td className="px-3 py-2 text-slate-700">
                      <Link to={`/vendor/farmer-links/${link.id}/passbook`}>
                        {link.farmerName}
                      </Link>
                    </td>
                    <td className="px-3 py-2">
                      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${LINK_STATUS_TONES[link.status]}`}>
                        {link.status}
                      </span>
                    </td>
                    <td className="text-right px-3 py-2 font-mono text-slate-900">
                      {formatMoney(Number(link.outstandingBalance ?? 0))}
                    </td>
                    <td className="text-right px-3 py-2">
                      <div className="flex items-center gap-2">
                        {isPending && (
                          <>
                            <button
                              type="button"
                              disabled={acceptLink.isPending}
                              onClick={() => void acceptLink.mutate(link.id)}
                              className="rounded-md border border-transparent bg-brand-600 py-1 px-2 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                            >
                              Accept
                            </button>
                            <button
                              type="button"
                              disabled={rejectLink.isPending}
                              onClick={() => void rejectLink.mutate(link.id)}
                              className="rounded-md border border-slate-300 bg-white py-1 px-2 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                            >
                              Reject
                            </button>
                          </>
                        )}
                        {isActive && (
                          <button
                            type="button"
                            disabled={suspendLink.isPending}
                            onClick={() => void suspendLink.mutate(link.id)}
                            className="rounded-md border border-slate-300 bg-white py-1 px-2 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                          >
                            Suspend
                          </button>
                        )}
                        {isSuspended && (
                          <button
                            type="button"
                            disabled={reactivateLink.isPending}
                            onClick={() => void reactivateLink.mutate(link.id)}
                            className="rounded-md border border-slate-300 bg-white py-1 px-2 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                          >
                            Reactivate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}
