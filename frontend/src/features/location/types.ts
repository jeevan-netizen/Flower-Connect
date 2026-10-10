/**
 * Service locations (plan task 4.2) — the seeded `service_locations` reference
 * data, shared by the customer location picker and the vendor registration
 * form.
 *
 * These types mirror one backend contract:
 *   `GET /api/v1/locations` -> {@link ServiceLocationGroup}[] (hierarchical)
 */

/**
 * One service area from `GET /api/v1/locations`.
 *
 * `id` is the `service_locations.id` primary key and is the only thing the
 * client ever submits: the backend resolves coordinates and validates the
 * foreign key from it, so guessing one would be a data-integrity bug. The
 * centroid coordinates travel with the row so a selection can be stored and
 * displayed without a second lookup (the D-4 precedent).
 */
export interface ServiceLocationArea {
  id: number;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
}

/** The API groups service areas by city: one entry per city, in city order. */
export interface ServiceLocationGroup {
  city: string;
  areas: ServiceLocationArea[];
}

/** One flattened `<option>`: the id is the value, the label is human-readable. */
export interface ServiceLocationOption {
  id: string;
  city: string;
  label: string;
}

/**
 * Flattens the city-grouped response into option rows, dropping empty cities and
 * skipping areas with no usable id (a response shape the picker cannot submit).
 */
export function toServiceLocationOptions(
  groups: ServiceLocationGroup[] | undefined,
): ServiceLocationOption[] {
  if (!groups) return [];

  return groups.flatMap((group) =>
    group.areas
      .filter((area) => typeof area.id === "number" && area.id > 0)
      .map((area) => ({
        id: String(area.id),
        city: group.city,
        label: `${area.area} (${area.pincode})`,
      })),
  );
}

/** True when the response carried no selectable area at all. */
export function hasNoServiceLocations(options: ServiceLocationOption[]): boolean {
  return options.length === 0;
}

/**
 * The customer's chosen delivery location, held for the browsing session.
 *
 * The whole flattened row is stored rather than only the id so the application
 * shell can render the selection on the very first paint after a refresh,
 * without waiting for the locations request. The id is still the authoritative
 * key: `useSelectedLocation` validates the stored row against the live list on
 * every load, so a selection pointing at an area that no longer exists is
 * detected and cleared rather than silently displayed (see
 * `docs/decisions.md`, D-34).
 */
export interface SelectedLocation {
  id: number;
  city: string;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
}

/**
 * Resolves one `service_locations` row by id out of the hierarchical response,
 * together with the city that groups it.
 *
 * This is the single path from "the user picked option N" to a
 * {@link SelectedLocation}: the coordinates always come from the server's row,
 * never from the option label or from anything the client typed. No browser
 * Geolocation API and no GPS is involved anywhere (D-4).
 */
export function findSelectedLocation(
  cities: ServiceLocationGroup[] | undefined,
  id: number,
): SelectedLocation | null {
  if (!cities) return null;

  for (const group of cities) {
    const area = group.areas.find((candidate) => candidate.id === id);
    if (area) {
      return {
        id: area.id,
        city: group.city,
        area: area.area,
        pincode: area.pincode,
        latitude: area.latitude,
        longitude: area.longitude,
      };
    }
  }
  return null;
}

/**
 * The shell's compact rendering of a selection: area plus pincode.
 *
 * The city is deliberately omitted — the picker's `<optgroup>`s already show it
 * while choosing, and the header has room for the area, not a sentence. Tests
 * assert this exact format so the shell and the picker cannot drift into two
 * spellings of the same place.
 */
export function formatSelectedLocation(location: SelectedLocation): string {
  return `${location.area}, ${location.pincode}`;
}

/**
 * Type guard for a value restored from `sessionStorage`.
 *
 * A persisted selection is untrusted input: it can be absent, be valid JSON of
 * the wrong shape, or carry fields of the wrong type after a schema change.
 * Everything downstream treats a failed guard as "no selection" rather than
 * letting a malformed row reach the discovery and search queries of tasks 4.3
 * and 4.4.
 */
export function isSelectedLocation(value: unknown): value is SelectedLocation {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.id === "number" &&
    Number.isInteger(candidate.id) &&
    candidate.id > 0 &&
    typeof candidate.city === "string" &&
    typeof candidate.area === "string" &&
    typeof candidate.pincode === "string" &&
    typeof candidate.latitude === "number" &&
    typeof candidate.longitude === "number"
  );
}
