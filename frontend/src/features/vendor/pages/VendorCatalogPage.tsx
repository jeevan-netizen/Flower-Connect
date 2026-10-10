import { useState } from "react";
import { Link } from "react-router-dom";
import {
  PRODUCT_PAGE_SIZE,
  type ProductListFilters,
  type ProductStatus,
} from "@/features/vendor/types";
import { useCategories, useProducts } from "@/features/vendor/queries";
import { CatalogFilters } from "@/features/vendor/components/CatalogFilters";
import { ProductTable } from "@/features/vendor/components/ProductTable";
import { CatalogLoader } from "@/features/vendor/components/ApprovedVendorGate";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const EMPTY_FILTERS: ProductListFilters = { status: null, categoryId: null, name: "", page: 0 };

/**
 * The vendor catalog (plan task 3.9: "product list", search, filters, pagination,
 * low-stock badges).
 *
 * Filter state lives here rather than in the URL. The admin listings made the same
 * choice (D-16) and for the same reason: the filters are a means of finding a
 * product to edit, not a shareable address, and a filter state that had to be
 * re-encoded on every keystroke would be a second source of truth for the query
 * key. What *is* in the key is the filter object, so changing a filter is a cache
 * miss instead of a stale render of the previous filter's rows.
 *
 * The search term is committed separately from the box that holds it. The box
 * updates on every keystroke so typing feels immediate; the *query* only changes
 * when the term is submitted (Enter, the Search button, or leaving the field), so a
 * `LIKE '%term%'` over an unindexed column is not issued per character.
 *
 * Every filter change resets to page 0 — page 3 of an unfiltered listing is
 * meaningless once the result narrows to one page.
 */
export function VendorCatalogPage() {
  const [filters, setFilters] = useState<ProductListFilters>(EMPTY_FILTERS);
  const [searchDraft, setSearchDraft] = useState("");

  const { data: categories, error: categoriesError } = useCategories();
  const { data: products, isPending, isFetching, error, refetch } = useProducts(filters);

  const applyFilters = (next: Partial<ProductListFilters>) => {
    setFilters((current) => ({ ...current, ...next, page: 0 }));
  };

  const commitSearch = () => {
    const name = searchDraft.trim();
    if (name === filters.name) {
      return;
    }
    applyFilters({ name });
  };

  const clearFilters = () => {
    setSearchDraft("");
    setFilters(EMPTY_FILTERS);
  };

  const changePage = (page: number) => {
    setFilters((current) => ({ ...current, page: Math.max(0, page) }));
  };

  if (error) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHeading
          title="Catalog"
          description="Every product in your shop, with its stock and lifecycle state."
        />
        <Link
          to="/vendor/catalog/new"
          className={`rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Add product
        </Link>
      </div>

      {/*
        A failed category read must not take the listing down with it: the products
        are still readable, only the category picker is unavailable. Surfaced as a
        short note rather than a full-page error.
      */}
      {categoriesError && (
        <p role="alert" className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">
          Categories could not be loaded, so the category filter and the product form&rsquo;s category
          list are unavailable. Products still list normally.
        </p>
      )}

      <CatalogFilters
        search={searchDraft}
        onSearchChange={setSearchDraft}
        onSearchSubmit={commitSearch}
        status={filters.status}
        onStatusChange={(status: ProductStatus | null) => applyFilters({ status })}
        categoryId={filters.categoryId}
        onCategoryChange={(categoryId) => applyFilters({ categoryId })}
        categories={categories?.content ?? []}
        disabled={isFetching}
        onClear={clearFilters}
      />

      {isPending ? (
        <CatalogLoader label="Loading your products..." />
      ) : (
        <Card>
          <ProductTable products={products?.content ?? []} />
          {products && products.totalPages > 0 && (
            <div className="mt-4">
              <Pagination
                page={products}
                onPageChange={changePage}
                disabled={isFetching}
              />
            </div>
          )}
        </Card>
      )}

      <p className="text-xs text-slate-500">
        Showing up to {PRODUCT_PAGE_SIZE} products per page. Removing a product hides it from the
        storefront and keeps its stock history.
      </p>
    </div>
  );
}