import { useState } from "react";
import { useEligibleVendors, useRequestLink } from "@/features/farmer/queries";
import { FarmerErrorState } from "@/features/farmer/components/FarmerErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { AnimatedList } from "@/motion/AnimatedList";

/**
 * Farmer's vendor discovery view: search approved vendors, see each one's link
 * status, and send a join request. Reuses the backend's approved-vendor
 * visibility rules — only `APPROVED` vendors are listed.
 */
export function FarmerVendorBrowsePage() {
  const [query, setQuery] = useState("");
  const { data: vendors, isPending, error, refetch } = useEligibleVendors(
    query || undefined,
  );
  const requestLink = useRequestLink();

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading vendors…
      </div>
    );
  }

  if (error) {
    return <FarmerErrorState error={error} onRetry={() => void refetch()} />;
  }

  const pendingRequest = requestLink.isPending;

  return (
    <div className="space-y-6">
      <PageHeading
        title="Find a florist"
        description="Search approved vendors and request to join one."
      />

      <Card title="Search">
        <div className="flex flex-col sm:flex-row gap-3 sm:items-end">
          <input
            id="vendor-search"
            type="text"
            placeholder="Search by business name…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="flex-1 mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm"
          />
          <button
            type="button"
            disabled={requestLink.isPending}
            onClick={() => refetch()}
            className="rounded-md border border-transparent bg-brand-600 py-2 px-4 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Search
          </button>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Only approved florists appear here. Sending a request creates a
          "PENDING" link the vendor must accept.
        </p>
      </Card>

      <Card title="Approved vendors">
        {(vendors?.length ?? 0) === 0 ? (
          <p className="text-sm text-slate-600">
            No approved vendors match your search.
          </p>
        ) : (
          <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {(vendors ?? []).map((vendor) => (
              <div
                key={vendor.id}
                className="rounded-lg border border-slate-200 bg-white p-4 flex flex-col justify-between"
              >
                <div>
                  <h3 className="font-semibold text-slate-900">{vendor.businessName}</h3>
                  <p className="mt-0.5 text-sm text-slate-600">
                    {vendor.vendorStatus}
                  </p>
                </div>
                <div className="mt-3 flex items-start justify-between gap-2">
                  <div>
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      vendor.linkStatus === "ACTIVE"
                        ? "bg-brand-100 text-brand-900"
                        : vendor.linkStatus === "PENDING"
                          ? "bg-amber-100 text-amber-800"
                          : "bg-slate-200 text-slate-800"
                    }`}>
                      {vendor.linkStatus ?? "No link"}
                    </span>
                  </div>
                  <button
                    type="button"
                    disabled={
                      vendor.linkStatus !== null ||
                      vendor.vendorStatus !== "APPROVED" ||
                      pendingRequest
                    }
                    onClick={() => void requestLink.mutate(vendor.id)}
                    className="mt-2 w-full rounded-md border border-transparent bg-brand-600 py-1.5 px-3 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {vendor.linkStatus === "PENDING"
                      ? "Requested"
                      : vendor.linkStatus === "ACTIVE"
                        ? "Linked"
                        : "Request to join"}
                  </button>
                </div>
              </div>
            ))}
          </AnimatedList>
        )}
      </Card>
    </div>
  );
}
