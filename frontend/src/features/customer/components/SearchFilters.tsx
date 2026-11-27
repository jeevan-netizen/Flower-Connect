import { useId } from "react";
import type { Category } from "@/features/vendor/types";
import { SORT_LABELS, SORT_OPTIONS, type SortOption } from "@/features/customer/types";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block rounded-md border-bolder-line-control shadow-sm ${FIELD_TRANSITION} focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm`;

interface SearchFiltersProps {
  /** The raw search box value, untrimmed: trimming while typing eats spaces. */
  search: string;
  onSearchChange: (value: string) => void;
  onSearchSubmit: () => void;
  categoryId: number | null;
  onCategoryChange: (categoryId: number | null) => void;
  categories: Category[];
  /** The raw price fields, kept as text so a half-typed number is not lost. */
  priceMin: string;
  priceMax: string;
  onPriceChange: (bound: "min" | "max", value: string) => void;
  /**
   * Applies the price bounds.
   *
   * On blur rather than on every keystroke, for the same reason the search box
   * waits for a submit: a number field fires a change event per digit, and a
   * request per digit turns a price range into a stream of table scans.
   */
  onPriceCommit: () => void;
  /** Set when the two price bounds cannot both be true. */
  priceError: string | null;
  sort: SortOption;
  onSortChange: (sort: SortOption) => void;
  /** Disabled while a listing is in flight, so the controls cannot be double-fired. */
  disabled?: boolean;
  onClear: () => void;
}

/**
 * The customer search and filter panel (plan task 4.7).
 *
 * Four deliberate behaviours:
 *
 * 1. **The search box is a real form.** Pressing Enter submits, so the control
 *    works without a pointer, and the term is not sent on every keystroke — the
 *    backend's name match is a `LIKE '%term%'` over an unindexed column, so a
 *    request per character would put a table scan behind each letter. It runs
 *    on submit and on blur instead, exactly as the vendor catalog's search box
 *    does.
 * 2. **Price bounds are two labelled number inputs, not a slider.** The plan
 *    says "price slider", but a two-handle range slider has no native
 *    accessible semantics, cannot be typed into and is awkward with a screen
 *    reader. Two inputs mirror the backend's `priceMin`/`priceMax` contract
 *    exactly and are precise, keyboard-friendly and testable. This is a
 *    documented deviation from the plan's wording.
 * 3. **The category chips are the public list, which is top-level only.**
 *    `GET /api/v1/categories` returns the active root categories (D-17), so
 *    the chips are one level deep. The search endpoint accepts any active
 *    category id, including children, but a child is not discoverable from
 *    this list — that gap is known issue 025 and is not papered over here.
 * 4. **The category filter is exclusive**, matching the backend's exact-match
 *    rule: choosing a category does not also match its children.
 */
export function SearchFilters({
  search,
  onSearchChange,
  onSearchSubmit,
  categoryId,
  onCategoryChange,
  categories,
  priceMin,
  priceMax,
  onPriceChange,
  onPriceCommit,
  priceError,
  sort,
  onSortChange,
  disabled = false,
  onClear,
}: SearchFiltersProps) {
  const searchId = useId();
  const priceMinId = useId();
  const priceMaxId = useId();
  const sortId = useId();
  const priceErrorId = useId();

  const hasFilters =
    search.trim() !== "" ||
    categoryId !== null ||
    priceMin.trim() !== "" ||
    priceMax.trim() !== "";

  return (
    <div className="rounded-lg border border-bolder-line bg-bolder-surface p-4 shadow-sm">
      <form
        className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSearchSubmit();
        }}
      >
        <div>
          <label htmlFor={searchId} className="block text-sm font-medium text-bolder-text">
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
              className={`rounded-md bg-bolder-rose-action px-3 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Search
            </button>
          </div>
        </div>

        <div>
          <label htmlFor={priceMinId} className="block text-sm font-medium text-bolder-text">
            Minimum price
          </label>
          <input
            id={priceMinId}
            name="priceMin"
            type="number"
            inputMode="decimal"
            min={0}
            step={1}
            value={priceMin}
            disabled={disabled}
            onChange={(event) => onPriceChange("min", event.target.value)}
            onBlur={onPriceCommit}
            aria-invalid={priceError ? true : undefined}
            aria-describedby={priceError ? priceErrorId : undefined}
            className={`${CONTROL_CLASS} w-full`}
          />
        </div>

        <div>
          <label htmlFor={priceMaxId} className="block text-sm font-medium text-bolder-text">
            Maximum price
          </label>
          <input
            id={priceMaxId}
            name="priceMax"
            type="number"
            inputMode="decimal"
            min={0}
            step={1}
            value={priceMax}
            disabled={disabled}
            onChange={(event) => onPriceChange("max", event.target.value)}
            onBlur={onPriceCommit}
            aria-invalid={priceError ? true : undefined}
            aria-describedby={priceError ? priceErrorId : undefined}
            className={`${CONTROL_CLASS} w-full`}
          />
          {priceError && (
            <p id={priceErrorId} role="alert" className="mt-1 text-sm text-bolder-danger">
              {priceError}
            </p>
          )}
        </div>

        <div>
          <label htmlFor={sortId} className="block text-sm font-medium text-bolder-text">
            Sort by
          </label>
          <select
            id={sortId}
            value={sort}
            disabled={disabled}
            onChange={(event) => onSortChange(event.target.value as SortOption)}
            className={`${CONTROL_CLASS} w-full`}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {SORT_LABELS[option]}
              </option>
            ))}
          </select>
        </div>
      </form>

      {categories.length > 0 && (
        <div role="group" aria-label="Filter by category" className="mt-4">
          <span className="block text-sm font-medium text-bolder-text">Category</span>
          <div className="mt-2 flex gap-2 overflow-x-auto pb-1 sm:flex-wrap sm:overflow-visible">
            <button
              type="button"
              aria-pressed={categoryId === null}
              onClick={() => onCategoryChange(null)}
              disabled={disabled}
              className={`whitespace-nowrap rounded-full border px-3 py-1 text-sm ${
                categoryId === null
                  ? "border-bolder-rose-action bg-bolder-blush-light font-medium text-bolder-rose-deep"
                  : "border-bolder-line-control bg-bolder-surface text-bolder-text hover:border-bolder-rose"
              } disabled:opacity-50 ${FOCUS_RING}`}
            >
              All categories
            </button>
            {categories.map((category) => (
              <button
                key={category.id}
                type="button"
                aria-pressed={categoryId === category.id}
                onClick={() =>
                  onCategoryChange(categoryId === category.id ? null : category.id)
                }
                disabled={disabled}
                className={`whitespace-nowrap rounded-full border px-3 py-1 text-sm ${
                  categoryId === category.id
                    ? "border-bolder-rose-action bg-bolder-blush-light font-medium text-bolder-rose-deep"
                    : "border-bolder-line-control bg-bolder-surface text-bolder-text hover:border-bolder-rose"
                } disabled:opacity-50 ${FOCUS_RING}`}
              >
                {category.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {hasFilters && (
        <button
          type="button"
          onClick={onClear}
          disabled={disabled}
          className={`mt-3 text-sm font-medium text-bolder-rose-strong hover:text-bolder-rose-deep disabled:opacity-50 ${FOCUS_RING}`}
        >
          Clear filters
        </button>
      )}
    </div>
  );
}
