import { create } from "zustand";
import { DEFAULT_SEARCH_FILTERS, type SearchFilters } from "@/features/customer/types";

interface SearchState {
  /** The applied filters, including the page the customer is on. */
  filters: SearchFilters;
  /**
   * Applies a filter change.
   *
   * A filter change resets to the first page, because page 3 of a narrowed
   * result set is a request the server answers with an empty page and no
   * explanation.
   */
  setFilters: (next: Partial<SearchFilters>) => void;
  /** Moves to another page of the current result set. */
  setPage: (page: number) => void;
  /** Returns to the default filters — the "clear filters" action. */
  reset: () => void;
}

/**
 * The customer's search filters and page (plan task 4.7).
 *
 * The state lives here rather than in the page component, because the app
 * shell keys its route transition by the full pathname: every pathname change
 * remounts the routed subtree. A filter held in the page would therefore be
 * lost the moment the customer opened a product and came back — and because
 * the filters are part of the search query key, losing them also changes the
 * key and refetches the unfiltered results.
 *
 * The store is **in memory only**: nothing is written to `localStorage` or
 * `sessionStorage`, so a full reload starts from the default filters. That is
 * the correct reading of "this application session" — the delivery location
 * is the one piece of customer context that is deliberately remembered across
 * a reload (D-34), because the server's answers depend on it.
 */
export const useSearchStore = create<SearchState>()((set) => ({
  filters: { ...DEFAULT_SEARCH_FILTERS },
  setFilters: (next) =>
    set((state) => ({ filters: { ...state.filters, ...next, page: 0 } })),
  setPage: (page) => set((state) => ({ filters: { ...state.filters, page } })),
  reset: () => set({ filters: { ...DEFAULT_SEARCH_FILTERS } }),
}));
