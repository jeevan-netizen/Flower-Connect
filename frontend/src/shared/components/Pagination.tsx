import { displayPageNumber } from "@/shared/format";
import type { PageResponse } from "@/shared/types";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const PAGE_BUTTON_CLASS = `rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`;

interface PaginationProps<T> {
  /** The page response as the backend returned it. */
  page: PageResponse<T>;
  onPageChange: (page: number) => void;
  /** Disabled while a mutation invalidates the list, to avoid a paging race. */
  disabled?: boolean;
}

/**
 * Pagination control shared by every paginated listing: the two admin listings
 * (plan task 2.10), the vendor catalog (task 3.9) and the vendor low-stock list
 * (task 3.9). It is generic over the page's content type because the envelope
 * is the same `PageResponse` in every case.
 *
 * The buttons are driven by the backend's own `first` / `last` / `totalPages`
 * rather than recomputed from `content.length`, so the control agrees with the
 * server about the boundaries instead of guessing at them. `page` is zero-based
 * on the wire and shown one-based to the operator.
 */
export function Pagination<T>({ page, onPageChange, disabled = false }: PaginationProps<T>) {
  return (
    <nav
      className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4"
      aria-label="Pagination"
    >
      <p className="text-sm text-slate-600">
        {page.totalElements === 0
          ? "No results"
          : `Page ${displayPageNumber(page.page)} of ${Math.max(page.totalPages, 1)}`}
        <span className="ml-2 text-slate-500">
          {page.totalElements} total{page.totalElements === 1 ? "" : "s"}
        </span>
      </p>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page.page - 1)}
          disabled={disabled || page.first || page.page === 0}
          className={PAGE_BUTTON_CLASS}
        >
          Previous
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page.page + 1)}
          disabled={disabled || page.last}
          className={PAGE_BUTTON_CLASS}
        >
          Next
        </button>
      </div>
    </nav>
  );
}