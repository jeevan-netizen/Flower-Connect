import api from "@/shared/lib/api";
import type {
  AddressPageResponse,
  CheckoutQuoteRequest,
  CheckoutQuoteResponse,
  CheckoutRequest,
  CheckoutResponse,
  SlotResponse,
} from "@/features/checkout/types";

/** `POST /api/v1/checkout/quote` — get a per-shop breakdown before placing an order. Requires `ROLE_CUSTOMER`. */
export async function fetchCheckoutQuote(payload: CheckoutQuoteRequest): Promise<CheckoutQuoteResponse> {
  const response = await api.post<CheckoutQuoteResponse>(`/checkout/quote`, payload);
  return response.data;
}

/** `POST /api/v1/checkout` — place the order, reserve stock, clear the cart. Requires `ROLE_CUSTOMER`. */
export async function placeCheckout(payload: CheckoutRequest): Promise<CheckoutResponse> {
  const response = await api.post<CheckoutResponse>(`/checkout`, payload);
  return response.data;
}

/** `GET /api/v1/vendors/{vendorId}/slots?addressId=&date=` — available delivery slots for one vendor. Requires `ROLE_CUSTOMER`. */
export async function fetchSlots(
  vendorId: number,
  params: { addressId: number; date: string },
  signal?: AbortSignal,
): Promise<SlotResponse[]> {
  const response = await api.get<SlotResponse[]>(`/vendors/${vendorId}/slots`, {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/addresses` — the customer's saved addresses, default first. Requires `ROLE_CUSTOMER`. */
export async function fetchAddresses(params?: { page?: number; size?: number }, signal?: AbortSignal): Promise<AddressPageResponse> {
  const response = await api.get<AddressPageResponse>(`/addresses`, {
    params,
    signal,
  });
  return response.data;
}
