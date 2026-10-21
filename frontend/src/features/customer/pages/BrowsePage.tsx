import { useState } from "react";
import { Link } from "react-router-dom";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";
import { useDiscovery } from "@/features/customer/queries";
import { LocationPrompt } from "@/features/customer/components/LocationPrompt";
import { VendorCard } from "@/features/customer/components/VendorCard";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

/**
 * The public browse page (plan task 4.6).
 *
 * It shows the florists that deliver to the customer's chosen area, nearest
 * first. The list comes from `GET /api/v1/discover`, which the server has
 * already narrowed to approved, order-accepting florists within their delivery
 * radius — the page does not filter anything itself, because a filter the
 * browser applies could only ever be a guess at the server's rule.
 *
 * With no area chosen there is nothing to ask the server, so the page shows
 * the location prompt and the discovery query stays disabled: no
 * location-dependent request is made until an area exists.
 */
export function BrowsePage() {
  const selected = useSelectedLocation();
  const locationId = selected?.id ?? null;
  const [page, setPage] = useState(0);

  const discovery = useDiscovery(locationId, page);

  if (selected === null) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Browse florists"
          description="Find florists who deliver to your area."
        />
        <LocationPrompt />
      </div>
    );
  }

  const vendors = discovery.data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title="Florists near you"
        description={`Delivering to ${selected.area}, ${selected.pincode}`}
      />

      <div className="flex flex-wrap items-center gap-3">
        <Link
          to="/search"
          className={`inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Search all products
        </Link>
        <p className="text-sm text-slate-600">
          Looking for something specific? Search every product that can reach you.
        </p>
      </div>

      {discovery.isPending ? (
        <FadeIn role="status" aria-busy="true" className="text-sm text-slate-600">
          Looking for florists near you...
        </FadeIn>
      ) : discovery.error ? (
        <CustomerErrorState
          error={discovery.error}
          onRetry={() => void discovery.refetch()}
          notFoundTitle="No florists found"
          notFoundDescription="We could not find any florists for this area."
        />
      ) : vendors.length === 0 ? (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">
            No florists deliver to {selected.area} yet
          </h2>
          <p className="mt-2 text-sm text-slate-600">
            Try another delivery area, or check back a little later.
          </p>
        </FadeIn>
      ) : (
        <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {vendors.map((vendor) => (
            <li key={vendor.id}>
              <VendorCard vendor={vendor} />
            </li>
          ))}
        </ul>
      )}

      {discovery.data && !discovery.isPending && !discovery.error && (
        <Pagination page={discovery.data} onPageChange={setPage} />
      )}
    </div>
  );
}
