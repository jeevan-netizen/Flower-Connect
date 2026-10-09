import { useCallback, useMemo } from "react";
import { useServiceLocations } from "@/features/location/queries";
import { useLocationStore } from "@/features/location/stores/location-store";
import {
  findSelectedLocation,
  hasNoServiceLocations,
  toServiceLocationOptions,
  type SelectedLocation,
  type ServiceLocationGroup,
} from "@/features/location/types";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";

interface LocationSelection {
  /** The validated current selection, or null. */
  selected: SelectedLocation | null;
  /** The city-grouped raw response, while it loads. */
  cities: ServiceLocationGroup[] | undefined;
  /** Flattened option rows derived from the response. */
  options: ReturnType<typeof toServiceLocationOptions>;
  isPending: boolean;
  error: Error | null;
  refetch: () => void;
  /**
   * Applies an option value (the area id, as the picker sends it) to the
   * session store, resolving the full row — coordinates included — from the
   * server's own data. An unknown value is a no-op: the shell never invents a
   * location the backend does not have.
   */
  selectById: (value: string) => void;
  clear: () => void;
}

/**
 * Everything a surface that lets the customer choose a location needs, in one
 * hook.
 *
 * The hero search and the shell's location menu are two renderings of the same
 * control over the same state, and the selection rules (validate against the
 * live list, coordinates only ever from the server's row) belong in one place:
 * a rule duplicated across two surfaces is a rule that drifts.
 */
export function useLocationSelection(): LocationSelection {
  const selected = useSelectedLocation();
  const { data: cities, isPending, error, refetch } = useServiceLocations();
  const select = useLocationStore((state) => state.select);
  const clear = useLocationStore((state) => state.clear);

  const options = useMemo(() => toServiceLocationOptions(cities), [cities]);

  const selectById = useCallback(
    (value: string) => {
      const location = findSelectedLocation(cities, Number(value));
      if (location) {
        select(location);
      }
    },
    [cities, select],
  );

  const refetchLocations = useCallback(() => {
    void refetch();
  }, [refetch]);

  return {
    selected,
    cities,
    options,
    isPending,
    error,
    refetch: refetchLocations,
    selectById,
    clear,
  };
}

/** True while the list has loaded successfully and carried no selectable area. */
export function isLocationListEmpty(selection: LocationSelection): boolean {
  return (
    !selection.isPending &&
    selection.error === null &&
    hasNoServiceLocations(selection.options)
  );
}