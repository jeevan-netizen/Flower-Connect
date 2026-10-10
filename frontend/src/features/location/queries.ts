import { useQuery } from "@tanstack/react-query";
import { fetchServiceLocations } from "@/features/location/api";

/**
 * Service areas are public, shared reference data, so they get their own top-level
 * key rather than living under the vendor key: the vendor area (task 2.9) caches the
 * signed-in vendor's own profile under `["vendor", "profile"]`, and logging out
 * clears that. Service areas must survive logout — the picker sits in the
 * application shell, so tying them to the vendor key would mean clearing the data
 * a signed-out visitor needs at exactly the moment they need it.
 *
 * The same key serves the vendor registration form and the customer location
 * picker: one cached fetch for both surfaces, because the endpoint is the same
 * public reference list.
 */
export const serviceLocationKeys = {
  all: ["locations"] as const,
  list: ["locations", "list"] as const,
};

export function useServiceLocations() {
  return useQuery({
    queryKey: serviceLocationKeys.list,
    queryFn: fetchServiceLocations,
  });
}
