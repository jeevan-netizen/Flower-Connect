import { useEffect, useId, useState } from "react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";
import { usePublicProduct } from "@/features/customer/queries";
import type { ProductSurfaceContext } from "@/features/customer/navigation";
import { LocationPrompt } from "@/features/customer/components/LocationPrompt";
import { ProductImagePlaceholder } from "@/features/customer/components/ProductImagePlaceholder";
import { AddToCartButton } from "@/features/customer/components/AddToCartButton";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { formatAvailability, formatMoney } from "@/features/customer/format";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { CART_MAX_QUANTITY } from "@/features/cart/types";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

/** The backend caps the gift note at 1000 characters (`@Size(max = 1000)`). */
const NOTE_MAX_LENGTH = 1000;

/**
 * The 404 a malformed product id is answered with.
 *
 * A URL like `/vendors/7/products/abc` matches the route, so the id has to
 * be checked before any request is made — otherwise the client would ask the
 * server about a product called "NaN". The wording is the same
 * non-disclosing message every other unavailable product gets, so a broken
 * link reveals no more than a missing one does.
 */
const MALFORMED_PRODUCT_ID_ERROR = {
  isAxiosError: true,
  response: { status: 404, data: { code: "NOT_FOUND", message: "Not found" } },
};

/**
 * One product's public detail (plan task 4.9).
 *
 * The endpoint requires a delivery location, because a product the customer
 * cannot receive is not something to offer them: with no area chosen the page
 * shows the location prompt and the detail query stays disabled, so no
 * location-dependent request is made.
 *
 * The page has two presentations. Opened from a florist's storefront or from
 * the search results — an in-app navigation — it renders as a panel **over**
 * that surface, with the surface still behind it and a close control that
 * returns to it. Reached directly, it renders as a page of its own with a
 * return link. The two are told apart by an explicit marker the product link
 * carries: the surface presents the product as an overlay only when the
 * marker belongs to the current page load, so a direct entry and a reload
 * both render the product on its own.
 */
export function ProductDetailPage() {
  const { productId } = useParams();
  const numericProductId = Number(productId);
  const validProductId = Number.isFinite(numericProductId) && numericProductId > 0;

  // The hosting surface says how the product is being shown. Without one —
  // the product route rendered on its own — the product is a standalone page.
  const surface = useOutletContext<ProductSurfaceContext | undefined>();
  const isOverlay = surface?.isOverlay === true;
  const returnPath = surface?.returnPath;
  const returnLabel = surface?.returnLabel;
  const close = surface?.close;

  const selected = useSelectedLocation();
  const locationId = selected?.id ?? null;

  const product = usePublicProduct(
    validProductId ? numericProductId : 0,
    locationId,
    validProductId,
  );

  const [note, setNote] = useState("");
  const [quantityInput, setQuantityInput] = useState("1");

  /**
   * The quantity as a number.
   *
   * The field keeps the customer's text rather than being driven straight from
   * a number, so clearing it does not snap back to 1 mid-edit: a controlled
   * number input that re-renders its own value on every keystroke fights the
   * caret. The number the cart receives is the parsed value, never below 1 and
   * never above the cart's own maximum.
   */
  const quantity = Math.min(
    CART_MAX_QUANTITY,
    Math.max(1, Math.floor(Number(quantityInput)) || 1),
  );

  const titleId = useId();
  const noteId = useId();
  const quantityId = useId();

  useEffect(() => {
    if (!isOverlay || !close) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [isOverlay, close]);

  if (!validProductId) {
    return (
      <CustomerErrorState
        error={MALFORMED_PRODUCT_ID_ERROR}
        notFoundTitle="This product is not available"
        notFoundDescription="It may be sold out, or the shop may not deliver to your area."
      />
    );
  }

  if (locationId === null) {
    return (
      <div className="space-y-6">
        <PageHeading title="Product" />
        <LocationPrompt />
      </div>
    );
  }

  if (product.isPending) {
    return (
      <p role="status" aria-busy="true" className="text-sm text-slate-600">
        Loading product...
      </p>
    );
  }

  if (product.error) {
    return (
      <CustomerErrorState
        error={product.error}
        onRetry={() => void product.refetch()}
        notFoundTitle="This product is not available"
        notFoundDescription="It may be sold out, or the shop may not deliver to your area."
      />
    );
  }

  const item = product.data;

  // The cover is the product's primary image; the first image is the fallback
  // when none is marked primary.
  const image = item.images.find((candidate) => candidate.primary) ?? item.images[0];

  const detail = (
    <div className="space-y-6">
      {!isOverlay && returnPath && (
        <Link
          to={returnPath}
          className={`inline-flex items-center text-sm font-medium text-brand-700 hover:text-brand-900 ${FOCUS_RING}`}
        >
          Back to {returnLabel}
        </Link>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <ProductImagePlaceholder image={image} productName={item.name} />

        <div>
          <p className="text-sm text-slate-500">{item.vendorName}</p>
          <h1 id={titleId} className="mt-1 text-2xl font-bold text-slate-900">
            {item.name}
          </h1>
          <p className="mt-1 text-sm text-slate-500">{item.categoryName}</p>
          <p className="mt-3 text-2xl font-semibold text-slate-900">
            {formatMoney(item.basePrice)}
          </p>
          <p className="mt-1 text-sm text-slate-600">{formatAvailability(item.available)}</p>

          {item.description && (
            <p className="mt-4 text-sm text-slate-700">{item.description}</p>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-base font-semibold text-slate-900">Add to your cart</h2>

        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor={quantityId} className="block text-sm font-medium text-slate-700">
              Quantity
            </label>
            <input
              id={quantityId}
              type="number"
              inputMode="numeric"
              min={1}
              max={CART_MAX_QUANTITY}
              step={1}
              value={quantityInput}
              onChange={(event) => setQuantityInput(event.target.value)}
              onBlur={() => setQuantityInput(String(quantity))}
              className="mt-1 block w-24 rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm"
            />
          </div>

          <div>
            <label htmlFor={noteId} className="block text-sm font-medium text-slate-700">
              Gift note (optional)
            </label>
            <textarea
              id={noteId}
              value={note}
              maxLength={NOTE_MAX_LENGTH}
              rows={3}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Add a message with the flowers..."
              className="mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm"
            />
            <p className="mt-1 text-xs text-slate-500">
              {note.length} of {NOTE_MAX_LENGTH} characters
            </p>
          </div>
        </div>

        <div className="mt-4">
          <AddToCartButton
            productId={item.id}
            productName={item.name}
            quantity={quantity}
            note={note.trim() === "" ? null : note.trim()}
          />
        </div>
      </div>
    </div>
  );

  if (!isOverlay) {
    return detail;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 p-4 sm:p-8"
    >
      <div className="mx-auto max-w-3xl rounded-lg bg-slate-50 p-5 shadow-xl">
        <div className="flex justify-end">
          <button
            type="button"
            onClick={() => close?.()}
            aria-label="Close product details"
            className={`rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Close
          </button>
        </div>
        <div className="mt-2">{detail}</div>
      </div>
    </div>
  );
}
