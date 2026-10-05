import { useId } from "react";
import { PRODUCT_STATUSES, type Category, type ProductStatus } from "@/features/vendor/types";
import { formatProductStatus } from "@/features/vendor/format";
import { FOCUS_RING, FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;

interface CatalogFiltersProps {
  /** The raw search box value, untrimmed: trimming while typing eats spaces. */
  search: string;
  onSearchChange: (value: string) => void;
  onSearchSubmit: () => void;
  status: ProductStatus | null;
  onStatusChange: (status: ProductStatus | null) => void;
  categoryId: number | null;
  onCategoryChange: (categoryId: number | null) => void;
  categories: Category[];
  /** Disabled while a listing is in flight, so the controls cannot be double-fired. */
  disabled?: boolean;
  onClear: () => void;
}

/**
 * Search, status filter and category filter for the catalog listing
 * (plan task 3.9).
 *
 * Three deliberate behaviours:
 *
 * 1. **The search box is a real form.** Pressing Enter submits, so the control
 *    works without a pointer. The term is *not* sent on every keystroke: the
 *    backend's `nameContains` is a `LIKE '%term%'` over an unindexed column, so
 *    a request per character would put a table scan behind each letter. It runs on
 *    submit and on blur instead.
 * 2. **Any filter change resets to page 0.** Page 3 of an unfiltered listing is
 *    meaningless once a filter narrows the result to one page, and asking the
 *    server for page 3 of a one-page result is an empty table with no explanation.
 * 3. **An empty search term is not a filter.** It is submitted as absent, because
 *    `ProductSpecifications.nameContains` treats a blank term as "no filter" and a
 *    vendor who cleared the box expects the full list back, not zero rows.
 */
export function CatalogFilters({
  search,
  onSearchChange,
  onSearchSubmit,
  status,
  onStatusChange,
  categoryId,
  onCategoryChange,
  categories,
  disabled = false,
  onClear,
}: CatalogFiltersProps) {
  const searchId = useId();
  const statusId = useId();
  const categoryId_ = useId();
  const hasFilters = search.trim() !== "" || status !== null || categoryId !== null;

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <form
        className="grid grid-cols-1 gap-4 md:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault();
          onSearchSubmit();
        }}
      >
        <div>
          <label htmlFor={searchId} className="block text-sm font-medium text-slate-700">
            Search products
          </label>
          <div className="mt-1 flex gap-2">
            <input
              id={searchId}
              type="search"
              value={search}
              placeholder="Name contains..."
              disabled={disabled}
              onChange={(event) => onSearchChange(event.target.value)}
              onBlur={onSearchSubmit}
              className={`${CONTROL_CLASS} flex-1`}
            />
            <button
              type="submit"
              disabled={disabled}
              className={`rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Search
            </button>
          </div>
        </div>

        <div>
          <label htmlFor={statusId} className="block text-sm font-medium text-slate-700">
            Status
          </label>
          <select
            id={statusId}
            value={status ?? ""}
            disabled={disabled}
            onChange={(event) =>
              onStatusChange((event.target.value || null) as ProductStatus | null)
            }
            className={CONTROL_CLASS}
          >
            <option value="">All statuses</option>
            {PRODUCT_STATUSES.map((option) => (
              <option key={option} value={option}>
                {formatProductStatus(option)}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor={categoryId_} className="block text-sm font-medium text-slate-700">
            Category
          </label>
          <select
            id={categoryId_}
            value={categoryId ?? ""}
            disabled={disabled}
            onChange={(event) => {
              const raw = event.target.value;
              onCategoryChange(raw === "" ? null : Number(raw));
            }}
            className={CONTROL_CLASS}
          >
            <option value="">All categories</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>
      </form>

      {hasFilters && (
        <button
          type="button"
          onClick={onClear}
          disabled={disabled}
          className={`mt-3 text-sm font-medium text-brand-700 hover:text-brand-900 disabled:opacity-50 ${FOCUS_RING}`}
        >
          Clear filters
        </button>
      )}
    </div>
  );
}