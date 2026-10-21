import { useMemo, useState } from "react";
import { Outlet } from "react-router-dom";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";
import { useCategories, useSearch } from "@/features/customer/queries";
import { PRODUCT_ROUTE_IDS, useProductOverlay } from "@/features/customer/navigation";
import { useSearchStore } from "@/features/customer/stores/search-store";
import { LocationPrompt } from "@/features/customer/components/LocationPrompt";
import { SearchFilters } from "@/features/customer/components/SearchFilters";
import { ProductGrid } from "@/features/customer/components/ProductGrid";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";

interface PriceBounds {
  min: number | null;
  max: number | null;
  error: string | null;
}

/**
 * Reads the two price fields.
 *
 * A blank field is no bound at all, which is how the backend reads an absent
 * `priceMin`/`priceMax`. A field that is not a number is also treated as no
 * bound rather than as zero, because "0" is a real bound and "not a number" is
 * not. The only rule worth enforcing here is the one the backend enforces: a
 * minimum above a maximum can never match anything, so it is refused before
 * the request is made.
 */
function readPriceBounds(priceMin: string, priceMax: string): PriceBounds {
  const min = priceMin.trim() === "" ? null : Number(priceMin);
  const max = priceMax.trim() === "" ? null : Number(priceMax);

  const safeMin = min === null || Number.isNaN(min) ? null : min;
  const safeMax = max === null || Number.isNaN(max) ? null : max;

  if (safeMin !== null && safeMax !== null && safeMin > safeMax) {
    return {
      min: safeMin,
      max: safeMax,
      error: "The minimum price must not be greater than the maximum price.",
    };
  }

  return { min: safeMin, max: safeMax, error: null };
}

/**
 * The public search page (plan task 4.7).
 *
 * The filter state lives in an in-memory store, and is **not** written to the
 * URL: the same choice the vendor catalog and the two admin listings make, so
 * there is one place a filter is read from and no second copy in the address
 * bar that could disagree with what is on screen. The delivery location is not
 * part of the filter state either — it is read from the session store, which
 * is its single source (D-34).
 *
 * The store rather than this component, because the app shell keys its route
 * transition by the pathname: opening a product remounts the page, and a
 * filter held here would be lost with it — and because the filters are part
 * of the search query key, losing them would also refetch the unfiltered
 * results. The store is not persisted, so a full reload starts from the
 * defaults.
 *
 * Every filter change resets to page 0, because page 3 of a narrowed result
 * set is a request the server will answer with an empty page and no
 * explanation.
 */
export function SearchPage() {
  const selected = useSelectedLocation();
  const locationId = selected?.id ?? null;

  // The filters live in a store rather than in this component, because the
  // app shell keys its route transition by the pathname: opening a product
  // remounts the page, and a filter held here would be lost with it.
  const filters = useSearchStore((state) => state.filters);
  const setFilters = useSearchStore((state) => state.setFilters);
  const setPage = useSearchStore((state) => state.setPage);
  const resetFilters = useSearchStore((state) => state.reset);

  // The text fields are seeded from the applied filters, so the controls
  // agree with the results after the page has been remounted.
  const [searchInput, setSearchInput] = useState(filters.q);
  const [priceMinInput, setPriceMinInput] = useState(
    filters.priceMin === null ? "" : String(filters.priceMin),
  );
  const [priceMaxInput, setPriceMaxInput] = useState(
    filters.priceMax === null ? "" : String(filters.priceMax),
  );

  // The search surface is also the layout for its product-detail child, so a
  // product opened from the results renders over them.
  const surface = useProductOverlay(PRODUCT_ROUTE_IDS.search, "/search", "the results");
  const standaloneProduct = surface.productChildMatched && !surface.isOverlay;

  const categories = useCategories();
  // A standalone product entry has no results behind it, so the search is not
  // run — the request would be discarded.
  const results = useSearch(locationId, filters, !standaloneProduct);

  const bounds = useMemo(
    () => readPriceBounds(priceMinInput, priceMaxInput),
    [priceMinInput, priceMaxInput],
  );

  const outletContext = useMemo(
    () => ({
      isOverlay: surface.isOverlay,
      close: surface.close,
      returnPath: surface.returnPath,
      returnLabel: surface.returnLabel,
    }),
    [surface.isOverlay, surface.close, surface.returnPath, surface.returnLabel],
  );

  /** Applies a filter change and returns to the first page. */
  const apply = (next: Partial<typeof filters>) => {
    setFilters(next);
  };

  const applySearchTerm = () => {
    apply({ q: searchInput.trim() });
  };

  const applyPriceBounds = () => {
    if (bounds.error !== null) {
      return;
    }
    apply({ priceMin: bounds.min, priceMax: bounds.max });
  };

  const clearFilters = () => {
    resetFilters();
    setSearchInput("");
    setPriceMinInput("");
    setPriceMaxInput("");
  };

  if (locationId === null) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Search products"
          description="Find flowers from florists who deliver to your area."
        />
        <LocationPrompt />
      </div>
    );
  }

  if (standaloneProduct) {
    return <Outlet context={outletContext} />;
  }

  const products = results.data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title="Search products"
        description={
          selected
            ? `Showing what can be delivered to ${selected.area}, ${selected.pincode}.`
            : undefined
        }
      />

      <SearchFilters
        search={searchInput}
        onSearchChange={setSearchInput}
        onSearchSubmit={applySearchTerm}
        categoryId={filters.categoryId}
        onCategoryChange={(categoryId) => apply({ categoryId })}
        categories={categories.data?.content ?? []}
        priceMin={priceMinInput}
        priceMax={priceMaxInput}
        onPriceChange={(bound, value) => {
          if (bound === "min") {
            setPriceMinInput(value);
          } else {
            setPriceMaxInput(value);
          }
        }}
        onPriceCommit={applyPriceBounds}
        priceError={bounds.error}
        sort={filters.sort}
        onSortChange={(sort) => apply({ sort })}
        onClear={clearFilters}
      />

      {results.isPending ? (
        <FadeIn role="status" aria-busy="true" className="text-sm text-slate-600">
          Searching...
        </FadeIn>
      ) : results.error ? (
        <CustomerErrorState
          error={results.error}
          onRetry={() => void results.refetch()}
          notFoundTitle="Nothing to show"
          notFoundDescription="We could not read the products for this area."
        />
      ) : products.length === 0 ? (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">No products match</h2>
          <p className="mt-2 text-sm text-slate-600">
            Try a different search term, or clear the filters to see everything.
          </p>
        </FadeIn>
      ) : (
        <ProductGrid products={products} detailPathBase="/search/products" />
      )}

      {results.data && !results.isPending && !results.error && (
        <Pagination page={results.data} onPageChange={(page) => setPage(page)} />
      )}

      <Outlet context={outletContext} />
    </div>
  );
}
