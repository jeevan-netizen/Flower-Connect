import { useMemo, useState } from "react";
import { Link, Outlet, useParams } from "react-router-dom";
import { useSelectedLocation } from "@/features/location/hooks/useSelectedLocation";
import { useStorefront } from "@/features/customer/queries";
import { PRODUCT_ROUTE_IDS, useProductOverlay } from "@/features/customer/navigation";
import { ProductGrid } from "@/features/customer/components/ProductGrid";
import { StorefrontHours } from "@/features/customer/components/StorefrontHours";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { formatMoney, formatPrepTime } from "@/features/customer/format";
import { StarRating } from "@/features/review/components/StarRating";
import { usePublicVendorReviews } from "@/features/review/queries";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING } from "@/motion/pressable";
import { CARD, PAGE_CONTAINER } from "@/shared/ui/classes";
import type { ReviewResponse } from "@/features/review/types";

/**
 * The 404 a malformed florist id is answered with.
 *
 * A URL like `/vendors/abc` matches the route, so the id has to be checked
 * before any request is made — otherwise the client would ask the server
 * about a florist called "NaN". The wording is the same non-disclosing
 * message every other unavailable shop gets.
 */
const MALFORMED_VENDOR_ID_ERROR = {
  isAxiosError: true,
  response: { status: 404, data: { code: "NOT_FOUND", message: "Vendor not found" } },
};

/**
 * A florist's public storefront (plan task 4.8).
 *
 * The storefront endpoint takes no location and applies no radius filter: the
 * customer has already chosen this florist, so the storefront shows what the
 * florist sells. It is therefore readable without a delivery area, and a
 * missing area is shown as a **non-blocking** advisory rather than a gate —
 * unlike discovery, search and the product detail, whose endpoints require a
 * location and would answer 400 without one.
 *
 * The route is also the layout for its product-detail child, so a product
 * opened from here renders *over* this page with the storefront still behind
 * it. A product URL opened directly is a different case: there is no
 * storefront to sit behind, so this page hides itself and the product renders
 * on its own.
 */
export function VendorStorefrontPage() {
  const { vendorId } = useParams();
  const numericVendorId = Number(vendorId);
  const validVendorId = Number.isFinite(numericVendorId) && numericVendorId > 0;
  const [page, setPage] = useState(0);
  const [reviewPage, setReviewPage] = useState(0);

  const selected = useSelectedLocation();

  // Where a standalone product page offers to return to. A malformed
  // florist id has no storefront to go back to, so the catalogue is the
  // sensible destination.
  const surfacePath = validVendorId ? `/vendors/${numericVendorId}` : "/browse";
  const surface = useProductOverlay(PRODUCT_ROUTE_IDS.storefront, surfacePath, "the shop");

  // A standalone product entry has no shop behind it, so the shop is not
  // fetched at all — the request would be discarded. An overlay does have the
  // shop behind it, so there the request is made as usual.
  const standaloneProduct = surface.productChildMatched && !surface.isOverlay;
  const storefront = useStorefront(
    validVendorId ? numericVendorId : 0,
    page,
    validVendorId && !standaloneProduct,
  );

  // Public reviews for this vendor, newest first. Disabled for malformed ids.
  const publicReviews = usePublicVendorReviews(
    validVendorId ? numericVendorId : 0,
    reviewPage,
  );

  const outletContext = useMemo(
    () => ({
      isOverlay: surface.isOverlay,
      close: surface.close,
      returnPath: surface.returnPath,
      returnLabel: surface.returnLabel,
    }),
    [surface.isOverlay, surface.close, surface.returnPath, surface.returnLabel],
  );

  if (standaloneProduct) {
    return <Outlet context={outletContext} />;
  }

  if (!validVendorId) {
    return (
      <div className={PAGE_CONTAINER}>
        <CustomerErrorState
          error={MALFORMED_VENDOR_ID_ERROR}
          notFoundTitle="This shop is not available"
          notFoundDescription="This shop is not open to customers right now."
        />
      </div>
    );
  }

  if (storefront.isPending) {
    return (
      <div className={PAGE_CONTAINER}>
        <div className="space-y-8">
          <FadeIn role="status" aria-busy="true" className="text-sm text-bolder-muted">
            Loading shop...
          </FadeIn>
          {surface.productChildMatched && <Outlet context={outletContext} />}
        </div>
      </div>
    );
  }

  if (storefront.error) {
    return (
      <div className={PAGE_CONTAINER}>
        <div className="space-y-8">
          <CustomerErrorState
            error={storefront.error}
            onRetry={() => void storefront.refetch()}
            notFoundTitle="This shop is not available"
            notFoundDescription="This shop is not open to customers right now."
          />
          {surface.productChildMatched && <Outlet context={outletContext} />}
        </div>
      </div>
    );
  }

  const shop = storefront.data;

  return (
    <div className={PAGE_CONTAINER}>
      <div className="space-y-8">
        <div className={`${CARD} p-5 sm:p-7`}>
          <h1 className="font-display text-3xl leading-tight text-bolder-text sm:text-4xl">
            {shop.businessName}
          </h1>
          <p className="mt-2 text-sm text-bolder-muted">
            {shop.area}, {shop.city} {shop.pincode}
          </p>

          {shop.description && (
            <p className="mt-4 max-w-2xl text-sm leading-relaxed text-bolder-muted">
              {shop.description}
            </p>
          )}

          <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-4 text-sm lg:grid-cols-4">
            <div>
              <dt className="text-xs text-bolder-muted">Rating</dt>
              <dd className="mt-1 font-medium text-bolder-text">
                <StarRating rating={shop.avgRating} reviewCount={shop.reviewCount} />
              </dd>
            </div>
            <div>
              <dt className="text-xs text-bolder-muted">Prep time</dt>
              <dd className="mt-1 font-medium text-bolder-text">
                {formatPrepTime(shop.prepTimeMinutes)}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-bolder-muted">Minimum order</dt>
              <dd className="mt-1 font-medium text-bolder-text">
                {formatMoney(shop.minOrderAmount)}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-bolder-muted">Delivery</dt>
              <dd className="mt-1 font-medium text-bolder-text">
                {formatMoney(shop.baseDeliveryFee)}
              </dd>
            </div>
          </dl>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <h2 className="font-display text-2xl text-bolder-text">Products</h2>
            <div className="mt-4">
              <ProductGrid
                products={shop.products.content}
                vendorId={shop.id}
                locationId={selected?.id ?? null}
                emptyMessage="This shop has nothing available right now."
              />
            </div>
            <div className="mt-4">
              <Pagination page={shop.products} onPageChange={setPage} />
            </div>
          </div>

          <aside className="space-y-4">
            <div className={`${CARD} p-5`}>
              <h2 className="font-display text-xl text-bolder-text">Opening hours</h2>
              <div className="mt-3">
                <StorefrontHours hours={shop.hours} />
              </div>
            </div>

            {selected === null && (
              <FadeIn
                role="status"
                className="rounded-card border border-bolder-line bg-bolder-leaf-surface p-4 text-sm text-bolder-leaf-dark"
              >
                <p className="font-medium">Set your delivery area</p>
                <p className="mt-1">
                  Choose the area you want flowers delivered to, and we can confirm this
                  shop delivers to you.
                </p>
                <Link
                  to="/browse"
                  className={`mt-3 inline-flex items-center font-medium text-bolder-leaf-dark underline underline-offset-2 hover:text-bolder-text ${FOCUS_RING}`}
                >
                  Choose an area
                </Link>
              </FadeIn>
            )}
          </aside>
        </div>

        {/* Customer reviews for this vendor */}
        <div className={`${CARD} p-5 sm:p-6`}>
          <h2 className="mb-4 font-display text-2xl text-bolder-text">Customer reviews</h2>
          {publicReviews.isPending ? (
            <FadeIn role="status" aria-busy="true" className="text-sm text-bolder-muted">
              Loading reviews…
            </FadeIn>
          ) : publicReviews.error ? (
            <div className="rounded-card border border-danger-border bg-danger-surface p-4 text-sm text-danger">
              Could not load reviews. Please try again later.
            </div>
          ) : publicReviews.data?.content.length === 0 ? (
            <p className="text-sm text-bolder-muted">
              No reviews yet. Be the first to review this shop after a delivered order.
            </p>
          ) : (
            <ul className="space-y-4">
              {(publicReviews.data?.content as ReviewResponse[]).map((review) => (
                <li
                  key={review.id}
                  className="flex flex-col gap-2 border-t border-bolder-line pt-4 first:border-0 first:pt-0"
                >
                  <div className="flex items-center gap-2">
                    <StarRating rating={review.rating} reviewCount={1} className="text-sm" />
                    <span className="text-xs font-medium text-bolder-text">
                      {review.customerName}
                    </span>
                  </div>
                  {review.comment && (
                    <p className="text-sm leading-relaxed text-bolder-muted">{review.comment}</p>
                  )}
                  {review.vendorReply && (
                    <div className="mt-1 rounded-control border border-bolder-line bg-bolder-surface-muted p-3 text-sm">
                      <p className="text-xs font-semibold text-bolder-text">
                        {shop.businessName}:
                      </p>
                      <p className="mt-1 text-bolder-muted">{review.vendorReply}</p>
                    </div>
                  )}
                  <p className="text-xs text-bolder-muted">
                    {new Date(review.createdAt).toLocaleDateString(undefined, {
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                  </p>
                </li>
              ))}
            </ul>
          )}
          {publicReviews.data && !publicReviews.isPending && !publicReviews.error && (
            <div className="mt-4">
              <Pagination page={publicReviews.data} onPageChange={setReviewPage} />
            </div>
          )}
        </div>

        <Outlet context={outletContext} />
      </div>
    </div>
  );
}
