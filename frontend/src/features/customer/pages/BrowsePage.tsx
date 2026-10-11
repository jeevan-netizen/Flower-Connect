import { useCallback, useMemo, useState } from "react";
import { LocationPicker } from "@/features/location/components/LocationPicker";
import { useLocationSelection } from "@/features/location/hooks/useLocationSelection";
import { useDiscovery } from "@/features/customer/queries";
import { VendorCard } from "@/features/customer/components/VendorCard";
import { VendorCardSkeletonGrid } from "@/features/customer/components/VendorCardSkeleton";
import { Pagination } from "@/shared/components/Pagination";
import { toVendorCardView } from "@/features/customer/types";
import { AnimatedList } from "@/motion/AnimatedList";
import { FadeIn } from "@/motion/FadeIn";

const DEFAULT_PAGE_SIZE = 20;

interface BrowsePageProps {
  /** Optional initial page from URL (not used currently; session store is the source). */
  initialPage?: number;
}

/**
 * Customer browse page (plan task 4.6).
 *
 * Public route showing nearby vendors via `GET /api/v1/discover`.
 * Integrates the shared location picker from the header and session store.
 *
 * States handled:
 * 1. No location selected → guidance + picker, no discovery request
 * 2. Loading → vendor card skeletons
 * 3. Success → responsive vendor card grid with pagination
 * 4. Empty results → helpful message
 * 5. Error → clear message + retry
 * 6. Location change → resets pagination, refetches for new location
 */
export function BrowsePage({ initialPage = 0 }: BrowsePageProps) {
  const locationSelection = useLocationSelection();
  const { selected, cities, isPending: locationsPending, error: locationsError, refetch: refetchLocations } = locationSelection;

  const [currentPage, setCurrentPage] = useState(initialPage);

  const discoveryParams = useMemo(() => {
    if (!selected) return null;
    return {
      locationId: selected.id,
      page: currentPage,
      size: DEFAULT_PAGE_SIZE,
    };
  }, [selected, currentPage]);

  const discovery = useDiscovery(discoveryParams);
  const { data, isPending, isError, error, refetch, isFetching } = discovery;

  const handlePageChange = useCallback(
    (page: number) => {
      setCurrentPage(page);
    },
    [],
  );

  // Reset pagination when location changes
  useMemo(() => {
    if (selected) {
      setCurrentPage(0);
    }
  }, [selected?.id]);

const handleRetry = useCallback(() => {
    if (locationsError) {
      refetchLocations();
    } else {
      refetch();
    }
  }, [locationsError, refetchLocations, refetch]);

  const locationUnavailable = locationsPending || locationsError !== null;

  const locationHint = locationsPending
    ? "Loading delivery areas..."
    : locationsError
    ? "Could not load delivery areas. Please try again."
    : "We use this area to find florists who deliver to you.";

  const locationError = locationsError
    ? "Delivery areas could not be loaded."
    : undefined;

  const hasLocation = selected !== null;

  const handleLocationChange = useCallback(
    (value: string) => {
      if (value === "") return;
      locationSelection.selectById(value);
    },
    [locationSelection],
  );

  const empty = !isPending && !isError && data?.empty;
  const vendors = data?.content ?? [];

  return (
    <FadeIn className="max-w-7xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-slate-900">Discover Florists Near You</h1>
        <p className="mt-1 text-slate-600">
          Choose your delivery area to see florists who deliver to you.
        </p>
      </div>

      {/* Location Picker */}
      <section aria-labelledby="browse-location-heading" className="mb-8" data-testid="browse-location-section">
        <h2 id="browse-location-heading" className="sr-only">
          Delivery location
        </h2>
        <LocationPicker
          cities={cities}
          value={hasLocation ? String(selected!.id) : ""}
          hint={locationHint}
          error={locationError}
          disabled={locationUnavailable}
          onChange={handleLocationChange}
        />
        {locationsError && (
          <button
            type="button"
            onClick={refetchLocations}
            className="mt-2 inline-flex rounded-sm text-sm font-medium text-brand-700 underline underline-offset-2 hover:text-brand-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
          >
            Try again
          </button>
        )}
      </section>

      {/* Results */}
      <section aria-labelledby="results-heading">
        <h2 id="results-heading" className="sr-only">
          Florists in your area
        </h2>

        {!hasLocation ? (
          <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50">
            <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <h3 className="mt-4 text-lg font-medium text-slate-900">Set your delivery location</h3>
            <p className="mt-1 text-slate-600">
              Choose an area above to see florists who deliver to you.
            </p>
          </div>
        ) : isPending ? (
          <VendorCardSkeletonGrid count={4} />
        ) : isError ? (
          <div className="text-center py-12 border-2 border-dashed border-red-300 rounded-xl bg-red-50" role="alert">
            <svg className="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <h3 className="mt-4 text-lg font-medium text-red-900">Could not load florists</h3>
            <p className="mt-1 text-red-700">
              {error instanceof Error ? error.message : "Something went wrong. Please try again."}
            </p>
            <button
              type="button"
              onClick={handleRetry}
              className="mt-4 inline-flex items-center gap-2 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Try again
            </button>
          </div>
        ) : empty ? (
          <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50">
            <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <h3 className="mt-4 text-lg font-medium text-slate-900">No florists found</h3>
            <p className="mt-1 text-slate-600">
              No florists currently deliver to {selected?.area}, {selected?.pincode}.
              Try a different area or check back later.
            </p>
          </div>
        ) : (
          <>
            <AnimatedList className="grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
              {vendors.map((vendor) => (
                <VendorCard key={vendor.id} vendor={toVendorCardView(vendor)} />
              ))}
            </AnimatedList>

            {data && !data.empty && (
              <Pagination<typeof vendors[0]>
                page={data}
                onPageChange={handlePageChange}
                disabled={isFetching}
              />
            )}
          </>
        )}
      </section>
    </FadeIn>
  );
}
