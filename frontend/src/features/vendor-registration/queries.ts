import { useMutation, useQuery } from "@tanstack/react-query";
import { fetchServiceLocations, registerVendor } from "@/features/vendor-registration/api";
import type { VendorRegisterRequest } from "@/features/vendor-registration/types";

/**
 * Service areas are public, shared reference data, so they get their own top-level
 * key rather than living under the vendor key: the vendor area (task 2.9) caches the
 * signed-in vendor's own profile under `["vendor", "profile"]`, and logging out
 * clears that. Service areas must survive logout — the registration page is reached
 * while signed out, so tying them to the vendor key would mean clearing the data a
 * signed-out visitor needs at exactly the moment they need it.
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

/**
 * Registration is a one-shot write, so there is nothing to invalidate: it creates
 * an account and a profile that the vendor cannot read until they sign in, at which
 * point `["vendor", "profile"]` is fetched fresh.
 */
export function useRegisterVendor() {
  return useMutation({
    mutationFn: (payload: VendorRegisterRequest) => registerVendor(payload),
  });
}