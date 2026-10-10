import { useEffect, useMemo } from "react";
import { useLocationStore } from "@/features/location/stores/location-store";
import { toServiceLocationOptions, type SelectedLocation } from "@/features/location/types";
import { useServiceLocations } from "@/features/location/queries";

/**
 * The customer's chosen location, validated against the live service-area list.
 *
 * This is the read side of the selection: every consumer that needs to know
 * *where the user is* uses this hook rather than reading the store directly, so
 * the staleness rule has exactly one implementation.
 *
 * The stored row is trusted while the list is still loading — clearing it
 * eagerly would wipe a perfectly valid selection on every page refresh, because
 * "absent from the list" is indistinguishable from "the list has not arrived
 * yet". Once the list has loaded, a stored id that is not among the current
 * areas is stale (the area was removed or its id changed), and the selection is
 * cleared: displaying a location the backend would reject on discovery or
 * search (tasks 4.3/4.4) is worse than asking the user to pick again (D-34).
 */
export function useSelectedLocation(): SelectedLocation | null {
  const selected = useLocationStore((state) => state.selected);
  const clear = useLocationStore((state) => state.clear);
  const { data, isSuccess } = useServiceLocations();
  const options = useMemo(() => toServiceLocationOptions(data), [data]);

  const stale =
    isSuccess &&
    selected !== null &&
    !options.some((option) => option.id === String(selected.id));

  useEffect(() => {
    if (stale) {
      clear();
    }
  }, [stale, clear]);

  return stale ? null : selected;
}
