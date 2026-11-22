import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { fetchAddresses } from "@/features/checkout/api";
import {
  createAddress,
  deleteAddress,
  updateAddress,
} from "@/features/customer/address/api";
import type { AddressPageResponse } from "@/features/checkout/types";

/**
 * Query keys for the customer's address book on the Profile page.
 *
 * Kept separate from the checkout slice's `["checkout", "addresses"]` key so a
 * Profile-page address create/update/delete does not invalidate an in-flight
 * checkout address selection and vice versa.
 */
export const addressKeys = {
  all: ["profile", "addresses"] as const,
  list: (page: number, size: number) =>
    ["profile", "addresses", "list", page, size] as const,
};

/** `GET /api/v1/addresses` — the customer's saved addresses, default first. */
export function useAddresses(page = 0, size = 20) {
  return useQuery<AddressPageResponse>({
    queryKey: addressKeys.list(page, size),
    queryFn: ({ signal }) => fetchAddresses({ page, size }, signal),
  });
}

/** `POST /api/v1/addresses`. On success the address list is re-read. */
export function useCreateAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Parameters<typeof createAddress>[0]) =>
      createAddress(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: addressKeys.all });
    },
  });
}

/** `PUT /api/v1/addresses/{id}`. On success the address list is re-read. */
export function useUpdateAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: number;
      payload: Parameters<typeof updateAddress>[1];
    }) => updateAddress(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: addressKeys.all });
    },
  });
}

/** `DELETE /api/v1/addresses/{id}`. On success the address list is re-read. */
export function useDeleteAddress() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteAddress(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: addressKeys.all });
    },
  });
}

/** Drops every cached address query. Called on logout. */
export function clearAddressCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: addressKeys.all });
}
