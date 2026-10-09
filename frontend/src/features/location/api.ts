import api from "@/shared/lib/api";
import type { ServiceLocationGroup } from "@/features/location/types";

/**
 * `GET /api/v1/locations` — the seeded service areas, city-grouped.
 *
 * Called with no query parameters, which is the only form of the endpoint that
 * returns every area in one response (any `pincode`/`area`/`page`/`size` turns it
 * into a paginated search instead). The location picker needs the complete list
 * to offer a choice, and the demo region is a single city, so this is a one-shot
 * fetch rather than a search-as-you-type flow.
 *
 * Public endpoint: no Authorization required, which is why the picker works for
 * a signed-out visitor. This client was extracted from the vendor-registration
 * slice in task 4.2, because the customer shell and the vendor registration form
 * now share it and the customer shell must not import from a vendor feature.
 */
export async function fetchServiceLocations(): Promise<ServiceLocationGroup[]> {
  const response = await api.get<ServiceLocationGroup[]>("/locations");
  return response.data;
}
