import api from "@/shared/lib/api";
import type { AddressResponse } from "@/features/checkout/types";

/**
 * The address namespace under `/api/v1/addresses` (plan task 4.1).
 *
 * Read (`fetchAddresses`) and the `useAddresses` query live in the checkout
 * slice (`@/features/checkout/api`, `@/features/checkout/queries`), which this
 * slice reuses rather than duplicating. This slice owns the mutating operations
 * the checkout wizard does not need: create, update and delete.
 *
 * `AddressResponse` mirrors the backend `AddressResponse` exactly.
 */

/** Payload for creating or replacing an address. Mirrors `AddressRequest`. */
export interface AddressRequest {
  label: string;
  line1: string;
  line2?: string | null;
  serviceLocationId: number;
  defaultAddress?: boolean;
}

/** `POST /api/v1/addresses` — create an address. */
export async function createAddress(
  payload: AddressRequest,
): Promise<AddressResponse> {
  const response = await api.post<AddressResponse>("/addresses", payload);
  return response.data;
}

/** `PUT /api/v1/addresses/{id}` — full replacement of an address. */
export async function updateAddress(
  id: number,
  payload: AddressRequest,
): Promise<AddressResponse> {
  const response = await api.put<AddressResponse>(`/addresses/${id}`, payload);
  return response.data;
}

/** `DELETE /api/v1/addresses/{id}` — delete an address. */
export async function deleteAddress(id: number): Promise<void> {
  await api.delete(`/addresses/${id}`);
}
