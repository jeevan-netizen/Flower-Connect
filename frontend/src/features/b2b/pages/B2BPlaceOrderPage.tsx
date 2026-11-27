/**
 * Place a B2B order (Stage 3 of the B2B module).
 *
 * The buyer searches for an approved seller, picks one, chooses quantities for
 * the seller's orderable products, reviews a client-side subtotal, and submits.
 * The authoritative total is computed server-side on
 * `POST /api/v1/vendors/b2b/orders` and returned in the response; the browser
 * never invents a price or a transition.
 *
 * Loading, empty, validation and API error states are all explicit. The
 * "place order" button is disabled while a request is in flight so a vendor
 * cannot double-submit.
 */

import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSellers, useSellerProducts } from "@/features/b2b/queries";
import { placeB2BOrder } from "@/features/b2b/api";
import type { B2BProductSummary, SellerSummary } from "@/features/b2b/types";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { formatMoney } from "@/features/vendor/format";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError } from "@/shared/lib/api-error";

const PAGE_SIZE = 20;

function sellerMatches(seller: SellerSummary, query: string): boolean {
  const term = query.toLowerCase();
  return (
    seller.businessName.toLowerCase().includes(term) ||
    seller.area.toLowerCase().includes(term) ||
    seller.pincode.includes(term)
  );
}

export function B2BPlaceOrderPage() {
  const [query, setQuery] = useState("");
  const [selectedSeller, setSelectedSeller] = useState<SellerSummary | null>(null);
  const [lines, setLines] = useState<Record<number, number>>({});
  const [actionError, setActionError] = useState<{ message: string } | null>(null);
  const navigate = useNavigate();

  const { data: sellers, isPending: sellersPending, error: sellersError } = useSellers(
    query || undefined,
  );

  const sellerProducts = useSellerProducts(selectedSeller?.vendorId ?? null);

  const products: B2BProductSummary[] = sellerProducts.data ?? [];
  const productsPending = sellerProducts.isPending;
  const productsError = sellerProducts.error;

  // Ensure every shown product has a line entry.
  useCallback(() => {
    setLines((existing) => {
      const next: Record<number, number> = { ...existing };
      products.forEach((product) => {
        if (!(product.productId in next)) {
          next[product.productId] = 0;
        }
      });
      return next;
    });
  }, [products]);

  const hasFatalError = Boolean(sellersError || productsError);
  const subtotal = Object.entries(lines).reduce(
    (sum, [productId, quantity]) => {
      const product = products.find((p) => p.productId === Number(productId));
      return sum + (product ? product.unitPrice * quantity : 0);
    },
    0,
  );
  const orderableLines = Object.entries(lines).filter(
    ([, quantity]) => quantity > 0,
  );
  const canPlace = orderableLines.length > 0;

  const clearError = () => setActionError(null);

  const submitPlaceOrder = async () => {
    clearError();
    const payload = {
      sellerId: selectedSeller!.vendorId,
      items: orderableLines.map(([productId, quantity]) => ({
        productId: Number(productId),
        quantity,
      })),
      orderNote: null,
    };
    try {
      const order = await placeB2BOrder(payload);
      setActionError(null);
      void navigate(`/vendor/b2b/orders/${order.id}`);
    } catch (caught) {
      setActionError({ message: toApiError(caught).message });
    }
  };

  if (hasFatalError) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16">
        <PageHeading
          title="Place a B2B order"
          description="Buy wholesale from an approved florist."
        />
        <VendorErrorState
          error={sellersError ?? productsError}
          onRetry={() => {
            setQuery(query);
          }}
        />
      </div>
    );
  }

  return (
    <>
      {actionError && (
        <FadeIn role="alert" className="max-w-4xl mx-auto px-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-semibold">Could not place the order</p>
          <p className="mt-1">{actionError.message}</p>
          <button
            type="button"
            onClick={clearError}
            className="mt-3 inline-flex items-center rounded-md border border-transparent bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
          >
            Dismiss
          </button>
        </FadeIn>
      )}
      <div className="max-w-5xl mx-auto px-4 py-8 space-y-8">
        <PageHeading
          title="Place a B2B order"
          description="Search for an approved seller, choose quantities, and submit."
        />

        {/* 1. Seller search */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900 mb-3">Choose a seller</h2>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="relative w-full sm:w-72">
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search sellers by name, area or pincode"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
              />
            </div>
            <button
              type="button"
              disabled={!selectedSeller}
              onClick={() => setSelectedSeller(null)}
              className={`rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
            >
              Clear selection
            </button>
          </div>

          {sellersPending && !sellers && (
            <FadeIn className="rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-600 flex items-center gap-3">
              Loading sellers…
            </FadeIn>
          )}

          {!sellersPending && sellers && sellers.length === 0 && (
            <FadeIn className="rounded-lg border border-slate-200 bg-white p-4 text-center text-sm text-slate-500">
              No approved sellers match "{query}".
            </FadeIn>
          )}

          {!sellersPending && sellers && sellers.length > 0 && (
            <ul className="mt-3 divide-y divide-slate-100 rounded-md border border-slate-200 bg-white max-h-60 overflow-y-auto">
              {sellers
                .filter((seller) => sellerMatches(seller, query))
                .slice(0, PAGE_SIZE)
                .map((seller) => (
                  <li key={seller.vendorId}>
                    <button
                      type="button"
                      onClick={() => setSelectedSeller(seller)}
                      className={`w-full flex items-center justify-between px-3 py-2.5 text-left transition-colors ${
                        selectedSeller?.vendorId === seller.vendorId
                          ? "bg-brand-50 text-brand-900"
                          : "text-slate-700 hover:bg-slate-50"
                      }`}
                    >
                      <div>
                        <p className="font-medium text-slate-900">
                          {seller.businessName}
                        </p>
                        <p className="text-xs text-slate-500">
                          {seller.area}, {seller.pincode} ·
                          delivery radius {seller.deliveryRadiusKm} km
                        </p>
                      </div>
                      {selectedSeller?.vendorId === seller.vendorId && (
                        <span className="rounded-md bg-brand-100 px-2 py-0.5 text-xs font-semibold text-brand-800">
                          Selected
                        </span>
                      )}
                    </button>
                  </li>
                ))}
            </ul>
          )}
        </div>

        {/* 2. Product selection */}
        {selectedSeller && (
          <>
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-3">
                <span className="rounded-md bg-brand-100 px-2 py-0.5 text-xs font-semibold text-brand-800">
                  {selectedSeller.businessName}
                </span>
                <span className="text-xs text-slate-500 font-medium">
                  {products.length} orderable product{products.length === 1 ? "" : "s"}
                </span>
              </div>

              {productsPending && !products.length && (
                <FadeIn className="rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-600 flex items-center gap-3">
                  Loading products…
                </FadeIn>
              )}

              {!productsPending && products.length === 0 && (
                <FadeIn className="rounded-lg border border-slate-200 bg-white p-4 text-center text-sm text-slate-500">
                  This seller has no orderable products right now.
                </FadeIn>
              )}

              {!productsPending && products.length > 0 && (
                <table className="w-full text-left text-sm divide-y divide-slate-100">
                  <thead>
                    <tr className="text-xs uppercase tracking-wide text-slate-500">
                      <th scope="col" className="py-3 pr-4 font-medium">
                        Product
                      </th>
                      <th scope="col" className="py-3 pr-4 font-medium text-right">
                        Price
                      </th>
                      <th scope="col" className="py-3 pr-4 font-medium text-right">
                        Available
                      </th>
                      <th scope="col" className="py-3 font-medium text-right">
                        Quantity
                      </th>
                      <th scope="col" className="py-3 font-medium text-right">
                        Subtotal
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {products.map((product) => {
                      const quantity = lines[product.productId] ?? 0;
                      const lineTotal = product.unitPrice * quantity;
                      return (
                        <tr key={product.productId} className="align-top group">
                          <td className="py-3 pr-4">
                            <p className="font-medium text-slate-900">
                              {product.productName}
                            </p>
                            <p className="text-xs text-slate-500">
                              {product.unit} • {formatMoney(product.unitPrice)} each
                            </p>
                          </td>
                          <td className="py-3 pr-4 text-right text-slate-900">
                            {formatMoney(product.unitPrice)}
                          </td>
                          <td className="py-3 pr-4 text-right text-slate-900">
                            {product.availableStock}
                          </td>
                          <td className="py-3 pr-4 text-right">
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={quantity}
                              onChange={(event) => {
                                const next = Number(event.target.value) || 0;
                                setLines((current) => ({
                                  ...current,
                                  [product.productId]: next,
                                }));
                              }}
                              disabled={quantity > product.availableStock}
                              className={`w-20 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm text-right shadow-sm focus:border-brand-500 focus:ring-brand-500 ${
                                quantity > product.availableStock
                                  ? "border-red-500 bg-red-50"
                                  : ""
                              }`}
                            />
                            {quantity > product.availableStock && (
                              <p className="mt-1 text-xs text-red-700">
                                Only {product.availableStock} available.
                              </p>
                            )}
                          </td>
                          <td className="py-3 font-medium text-right text-slate-900">
                            {formatMoney(lineTotal)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>

            {/* 3. Review and submit */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex justify-between items-center mb-3">
                <h2 className="text-sm font-semibold text-slate-900">Review</h2>
                <span className="text-sm font-semibold text-slate-900">
                  Total: {formatMoney(subtotal)}
                </span>
              </div>
              <p className="text-xs text-slate-500 mb-4">
                The seller confirms the final total when the order is placed;
                prices and available stock are taken from their catalog at
                submission time.
              </p>
              <button
                type="button"
                disabled={!canPlace}
                onClick={submitPlaceOrder}
                className={`w-full rounded-md border border-transparent bg-brand-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
              >
                Place order
              </button>
              {!canPlace && selectedSeller && (
                <p className="mt-2 text-xs text-slate-500">
                  Choose at least one product with a quantity greater than zero
                  to place the order.
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </>
  );
}
