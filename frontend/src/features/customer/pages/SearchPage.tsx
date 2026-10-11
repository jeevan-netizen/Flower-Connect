import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { LocationPicker } from "@/features/location/components/LocationPicker";
import { useLocationSelection } from "@/features/location/hooks/useLocationSelection";
import { useSearch } from "@/features/customer/queries";
import { fetchCategories } from "@/features/location/api";
import { ProductCard } from "@/features/customer/components/ProductCard";
import { ProductCardSkeletonGrid } from "@/features/customer/components/ProductCardSkeleton";
import { Pagination } from "@/shared/components/Pagination";
import { toProductCardView } from "@/features/customer/types";
import { AnimatedList } from "@/motion/AnimatedList";
import { FadeIn } from "@/motion/FadeIn";

const DEFAULT_PAGE_SIZE = 20;
const SORT_OPTIONS = [
  { value: "distance", label: "Distance (nearest first)" },
  { value: "price_asc", label: "Price: low to high" },
  { value: "price_desc", label: "Price: high to low" },
  { value: "name", label: "Name (A–Z)" },
] as const;

interface SearchPageProps {
  /** Optional initial page from URL (not used currently; session store is the source). */
  initialPage?: number;
}

/**
 * Customer search page (plan task 4.7).
 *
 * Public route showing products via `GET /api/v1/search`.
 * Integrates the shared location picker from the header and session store.
 *
 * States handled:
 * 1. No location selected → guidance + picker, no search request
 * 2. Loading → product card skeletons
 * 3. Success → responsive product card grid with pagination
 * 4. Empty results → helpful message
 * 5. Error → clear message + retry
 * 6. Filter/location change → resets pagination, refetches for new parameters
 */
export function SearchPage({ initialPage = 0 }: SearchPageProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const locationSelection = useLocationSelection();
  const { selected, cities, isPending: locationsPending, error: locationsError, refetch: refetchLocations } = locationSelection;

  // Categories for chips (public endpoint returns active roots only)
  const { data: categoriesData, isPending: categoriesPending } = useQuery({
    queryKey: ["categories", "active-roots"],
    queryFn: fetchCategories,
    staleTime: 5 * 60_000,
  });

  // Parse initial filters from URL
  const initialQ = searchParams.get("q") ?? "";
  const initialCategory = searchParams.get("category") ? Number(searchParams.get("category")) : undefined;
  const initialPriceMin = searchParams.get("priceMin") ? Number(searchParams.get("priceMin")) : undefined;
  const initialPriceMax = searchParams.get("priceMax") ? Number(searchParams.get("priceMax")) : undefined;
  const sortFromUrl = searchParams.get("sort");
  const initialSort = (SORT_OPTIONS.some((o) => o.value === sortFromUrl) ? sortFromUrl : "distance") as "distance" | "price_asc" | "price_desc" | "name";
  const urlPage = searchParams.get("page") ? Number(searchParams.get("page")) : initialPage;

  const [q, setQ] = useState(initialQ);
  const [category, setCategory] = useState<number | undefined>(initialCategory);
  const [priceMin, setPriceMin] = useState<number | undefined>(initialPriceMin);
  const [priceMax, setPriceMax] = useState<number | undefined>(initialPriceMax);
  const [sort, setSort] = useState(initialSort);
  const [currentPage, setCurrentPage] = useState(urlPage);
  const [submitted, setSubmitted] = useState(false);

  // Build search params for the query
  const searchParamsObj = useMemo(() => {
    if (!selected) return null;
    return {
      locationId: selected.id,
      q: q.trim() || undefined,
      category,
      priceMin,
      priceMax,
      sort: sort === "distance" ? undefined : sort, // backend defaults to distance
      page: currentPage,
      size: DEFAULT_PAGE_SIZE,
    };
  }, [selected, q, category, priceMin, priceMax, sort, currentPage]);

  const search = useSearch(searchParamsObj);
  const { data, isPending, isError, error, refetch, isFetching } = search;

  // Sync URL with filters (debounced)
  useEffect(() => {
    if (!submitted) return;
    const params = new URLSearchParams();
    if (q.trim()) params.set("q", q.trim());
    if (category) params.set("category", String(category));
    if (priceMin !== undefined) params.set("priceMin", String(priceMin));
    if (priceMax !== undefined) params.set("priceMax", String(priceMax));
    if (sort !== "distance") params.set("sort", sort);
    if (currentPage > 0) params.set("page", String(currentPage));
    setSearchParams(params, { replace: true });
  }, [q, category, priceMin, priceMax, sort, currentPage, setSearchParams, submitted]);

  // Reset pagination when location changes
  useEffect(() => {
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

  const handleSubmit = useCallback((event: React.FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setCurrentPage(0);
  }, []);

  const handleClearFilters = useCallback(() => {
    setQ("");
    setCategory(undefined);
    setPriceMin(undefined);
    setPriceMax(undefined);
    setSort("distance");
    setCurrentPage(0);
    setSubmitted(true);
  }, []);

  const handlePageChange = useCallback(
    (page: number) => {
      setCurrentPage(page);
    },
    [],
  );

  const locationUnavailable = locationsPending || locationsError !== null;

  const locationHint = locationsPending
    ? "Loading delivery areas..."
    : locationsError
    ? "Could not load delivery areas. Please try again."
    : "We use this area to find products delivered to you.";

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
  const products = data?.content ?? [];

  const categories = categoriesData?.content ?? [];

  return (
    <FadeIn className="max-w-7xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-slate-900">Search Products</h1>
        <p className="mt-1 text-slate-600">
          Find flowers and arrangements from local florists near you.
        </p>
      </div>

      {/* Location Picker */}
      <section aria-labelledby="search-location-heading" className="mb-8" data-testid="search-location-section">
        <h2 id="search-location-heading" className="sr-only">
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

      {/* Search Form & Filters */}
      {!hasLocation ? (
        <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50">
          <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-slate-900">Set your delivery location</h3>
          <p className="mt-1 text-slate-600">
            Choose an area above to search for products delivered to you.
          </p>
        </div>
      ) : (
        <>
          <form onSubmit={handleSubmit} className="mb-8">
            <div className="flex flex-col sm:flex-row gap-4 mb-4">
              <div className="flex-1">
                <label htmlFor="search-q" className="sr-only">
                  Search products
                </label>
                <input
                  type="search"
                  id="search-q"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="Search products (e.g., roses, bouquet, lilies)..."
                  className="w-full rounded-md border border-slate-300 bg-white px-4 py-2.5 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500 focus:outline-none transition-colors"
                  autoComplete="off"
                />
              </div>
              <button
                type="submit"
                className="inline-flex items-center justify-center rounded-md bg-brand-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 transition-colors"
              >
                Search
              </button>
            </div>

            <div className="flex flex-wrap items-center gap-4 text-sm">
              {/* Category chips */}
              {!categoriesPending && categories.length > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-slate-500">Category:</span>
                  <div className="flex flex-wrap gap-2">
                    {categories.map((cat) => (
                      <button
                        key={cat.id}
                        type="button"
                        onClick={() => setCategory(category === cat.id ? undefined : cat.id)}
                        className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-medium transition-colors ${
                          category === cat.id
                            ? "bg-brand-100 text-brand-800"
                            : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                        }`}
                        aria-pressed={category === cat.id}
                      >
                        {cat.name}
                        {category === cat.id && (
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        )}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Price inputs */}
              <div className="flex items-center gap-2" aria-label="Price range">
                <label htmlFor="price-min" className="sr-only">Min price</label>
                <input
                  type="number"
                  id="price-min"
                  min="0"
                  step="0.01"
                  value={priceMin ?? ""}
                  onChange={(e) => setPriceMin(e.target.value ? Number(e.target.value) : undefined)}
                  placeholder="Min"
                  className="w-28 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500 focus:outline-none transition-colors"
                  aria-label="Minimum price"
                />
                <span className="text-slate-400">–</span>
                <label htmlFor="price-max" className="sr-only">Max price</label>
                <input
                  type="number"
                  id="price-max"
                  min="0"
                  step="0.01"
                  value={priceMax ?? ""}
                  onChange={(e) => setPriceMax(e.target.value ? Number(e.target.value) : undefined)}
                  placeholder="Max"
                  className="w-28 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500 focus:outline-none transition-colors"
                  aria-label="Maximum price"
                />
              </div>

              {/* Sort dropdown */}
              <div className="flex items-center gap-2">
                <label htmlFor="sort-select" className="sr-only">Sort by</label>
                <select
                  id="sort-select"
                  value={sort}
                  onChange={(e) => setSort(e.target.value as typeof sort)}
                  className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500 focus:outline-none transition-colors"
                  aria-label="Sort results"
                >
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Clear filters */}
              {(q || category || priceMin !== undefined || priceMax !== undefined || sort !== "distance") && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 transition-colors"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                  Clear filters
                </button>
              )}
            </div>
          </form>

          {/* Results info */}
          {submitted && data && (
            <p className="mb-4 text-sm text-slate-600">
              {data.totalElements} product{data.totalElements === 1 ? "" : "s"} found
              {data.totalPages > 1 && ` · Page ${data.page + 1} of ${data.totalPages}`}
            </p>
          )}

          {/* Results */}
          <section aria-labelledby="search-results-heading">
            <h2 id="search-results-heading" className="sr-only">
              Search results
            </h2>

            {isPending ? (
              <ProductCardSkeletonGrid count={4} />
            ) : isError ? (
              <div className="text-center py-12 border-2 border-dashed border-red-300 rounded-xl bg-red-50" role="alert">
                <svg className="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <h3 className="mt-4 text-lg font-medium text-red-900">Could not search products</h3>
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
                <h3 className="mt-4 text-lg font-medium text-slate-900">No products found</h3>
                <p className="mt-1 text-slate-600">
                  No products match your search{selected?.area ? ` in ${selected.area}, ${selected.pincode}` : ""}.
                  Try adjusting your filters or search terms.
                </p>
              </div>
            ) : (
              <>
                <AnimatedList className="grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
                  {products.map((product) => (
                    <ProductCard key={product.id} product={toProductCardView(product)} />
                  ))}
                </AnimatedList>

                {data && !data.empty && (
                  <Pagination<typeof products[0]>
                    page={data}
                    onPageChange={handlePageChange}
                    disabled={isFetching}
                  />
                )}
              </>
            )}
          </section>
        </>
      )}
    </FadeIn>
  );
}