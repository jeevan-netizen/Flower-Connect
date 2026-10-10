import { useMutation } from "@tanstack/react-query";
import { registerVendor } from "@/features/vendor-registration/api";
import type { VendorRegisterRequest } from "@/features/vendor-registration/types";

/**
 * Registration is a one-shot write, so there is nothing to invalidate: it creates
 * an account and a profile that the vendor cannot read until they sign in, at which
 * point `["vendor", "profile"]` is fetched fresh.
 *
 * The service-area list this form also needs comes from
 * `@/features/location/queries` — the endpoint is shared with the customer
 * location picker (task 4.2), so the query hook lives with the feature that owns
 * the data, not with the page that happened to use it first.
 */
export function useRegisterVendor() {
  return useMutation({
    mutationFn: (payload: VendorRegisterRequest) => registerVendor(payload),
  });
}
