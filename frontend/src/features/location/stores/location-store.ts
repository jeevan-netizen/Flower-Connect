import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { isSelectedLocation, type SelectedLocation } from "@/features/location/types";

/**
 * The `sessionStorage` key holding the chosen delivery location.
 *
 * `sessionStorage`, not `localStorage`, on purpose (D-34): the selection is
 * scoped to one browsing tab — it survives navigation and a page refresh inside
 * that tab, but a new tab or a new browser session starts with no location
 * chosen. A durable cross-session preference is a different product decision
 * ("remember my area between visits") that task 4.2 does not make, and the
 * contrast with the *auth* store's `localStorage` (known issue 022) is
 * deliberate: a location is not a credential.
 */
export const LOCATION_STORAGE_KEY = "fc-location";

interface LocationState {
  selected: SelectedLocation | null;
  select: (location: SelectedLocation) => void;
  clear: () => void;
}

/**
 * The session-scoped chosen location (plan task 4.2).
 *
 * Two restore guards stand between whatever is in `sessionStorage` and the
 * application, because a persisted selection is untrusted input:
 *
 * 1. **`merge` validates the shape.** A value that parses as JSON but is the
 *    wrong shape — an obsolete schema, a partial row, a string id — is dropped
 *    to `null` rather than trusted. Without this, a stale schema would reach the
 *    discovery and search queries of tasks 4.3/4.4 as a silently invalid
 *    `locationId`.
 * 2. **Malformed JSON never reaches the store.** Zustand's `persist` catches a
 *    deserialization failure during hydration and leaves the initial state, so
 *    a corrupt `sessionStorage` entry cannot break the application (it is
 *    overwritten by the next selection).
 *
 * A *well-formed but obsolete* selection — one whose id no longer exists in
 * `service_locations` — cannot be detected here, because the store knows
 * nothing about the server's list. That is what `useSelectedLocation` does: it
 * validates the stored id against the live list and clears the store when the
 * row is gone (D-34).
 *
 * Selecting a location touches no other state: this store holds one field and
 * the authentication store is a separate module, so choosing an area cannot log
 * a user out or disturb any cached query.
 */
export const useLocationStore = create<LocationState>()(
  persist(
    (set) => ({
      selected: null,
      select: (location) => set({ selected: location }),
      clear: () => set({ selected: null }),
    }),
    {
      name: LOCATION_STORAGE_KEY,
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ selected: state.selected }),
      merge: (persisted, current) => {
        const candidate = (persisted as { selected?: unknown } | undefined)?.selected;
        return {
          ...current,
          selected: isSelectedLocation(candidate) ? candidate : null,
        };
      },
    },
  ),
);
