import { useCallback, useMemo, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useStorefront } from "@/features/customer/queries";
import { ProductCardSkeletonGrid } from "@/features/customer/components/ProductCardSkeleton";
import { ProductDetailModal } from "@/features/customer/components/ProductDetailModal";
import { Pagination } from "@/shared/components/Pagination";
import { toStorefrontProductCardView, type StorefrontProductCardView } from "@/features/customer/types";
import { formatMoney } from "@/features/vendor/format";
import { AnimatedList } from "@/motion/AnimatedList";
import { FadeIn } from "@/motion/FadeIn";

const DEFAULT_PAGE_SIZE = 20;
const WEEKDAY_LABELS: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};

/**
 * Storefront product card component.
 *
 * Displays product name, category, price, stock status, and vendor name.
 * Uses a generic flower placeholder for images (D-31, Issue 020).
 */
function StorefrontProductCard({ product, onClick }: { product: StorefrontProductCardView; onClick?: () => void }) {
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (onClick && (event.key === "Enter" || event.key === " ")) {
      event.preventDefault();
      onClick();
    }
  };

  return (
    <article
      className="flex flex-col h-full bg-white rounded-xl border border-slate-200 shadow-card p-4 transition-shadow duration-micro ease-standard hover:shadow-card-hover"
      onClick={onClick}
      onKeyDown={handleKeyDown}
      tabIndex={onClick ? 0 : undefined}
      role={onClick ? "button" : "article"}
      aria-label={onClick ? `View details for ${product.name}` : undefined}
    >
      <div className="flex flex-col flex-1">
        {/* Generic flower placeholder */}
        <div className="mb-3 aspect-square bg-slate-100 rounded-lg flex items-center justify-center">
          <svg className="h-24 w-24 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M12 11.25a2.25 2.25 0 114.5 0 2.25 2.25 0 01-4.5 0zM4.5 15.75a4.5 4.5 0 009 0 4.5 4.5 0 00-9 0zM12 3.75a2.25 2.25 0 114.5 0 2.25 2.25 0 01-4.5 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M12 3v1.5m0 16.5V21m-4.5-13.5V3m7.5 1.5v1.5m0 16.5V21M3 12h1.5m16.5 0H21" />
          </svg>
        </div>

        <div className="flex flex-col flex-1">
          <h3 className="text-lg font-semibold text-slate-900 truncate">
            {product.name}
          </h3>
          <p className="mt-0.5 text-sm text-slate-600 truncate">
            {product.categoryName}
          </p>

          <div className="mt-auto pt-3 border-t border-slate-100 flex items-center justify-between">
            <span className="font-semibold text-slate-900">
              {formatMoney(product.basePrice)}
            </span>
            <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
              product.inStock
                ? "bg-brand-100 text-brand-800"
                : "bg-slate-100 text-slate-600"
            }`}>
              {product.inStock ? "In stock" : "Out of stock"}
            </span>
          </div>
        </div>
      </div>
    </article>
  );
}

/**
 * Public vendor storefront page (plan task 4.8).
 *
 * Public route showing a vendor's profile and ACTIVE product catalogue via
 * `GET /api/v1/vendors/{vendorId}/storefront`. Uses the shared root layout
 * and SiteHeader. No authentication required.
 *
 * States handled:
 * 1. Loading -- vendor and product skeletons
 * 2. Success -- vendor profile + responsive product card grid with pagination
 * 3. Empty catalogue -- valid storefront with no products
 * 4. Unavailable storefront -- 404 for unknown/non-approved vendor (indistinguishable)
 * 5. Other API errors -- clear message + retry
 * 6. Pagination -- shared pagination component
 * 7. Vendor navigation -- changing vendorId displays new vendor's data
 */
export function StorefrontPage() {
  const { vendorId: vendorIdParam } = useParams<{ vendorId: string }>();
  const vendorId = vendorIdParam ? Number(vendorIdParam) : NaN;

  const [currentPage, setCurrentPage] = useState(0);

  const isValidVendorId = Number.isInteger(vendorId) && vendorId > 0;

  const storefrontParams = useMemo(() => {
    if (!isValidVendorId) return null;
    return {
      vendorId,
      page: currentPage,
      size: DEFAULT_PAGE_SIZE,
    };
  }, [isValidVendorId, vendorId, currentPage]);

  const storefront = useStorefront(storefrontParams);
  const { data, isPending, isError, error, refetch, isFetching } = storefront;

  const handlePageChange = useCallback(
    (page: number) => {
      setCurrentPage(page);
    },
    [],
  );

  const handleRetry = useCallback(() => {
    refetch();
  }, [refetch]);

  // Reset pagination when vendorId changes
  useMemo(() => {
    setCurrentPage(0);
  }, [vendorId]);

  // Product detail modal state
  const [selectedProduct, setSelectedProduct] = useState<StorefrontProductCardView | null>(null);

  const openProductDetail = useCallback((product: StorefrontProductCardView) => {
    setSelectedProduct(product);
  }, []);

  const closeProductDetail = useCallback(() => {
    setSelectedProduct(null);
  }, []);

  if (!isValidVendorId) {
    return (
      <FadeIn className="max-w-7xl mx-auto px-4 py-8">
        <div className="text-center py-12 border-2 border-dashed border-red-300 rounded-xl bg-red-50" role="alert">
          <svg className="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77-1.333.192 3 1.732 3z" />
          </svg>
          <h2 className="mt-4 text-lg font-medium text-red-900">Invalid vendor</h2>
          <p className="mt-1 text-red-700">The vendor identifier is not valid.</p>
          <Link
            to="/browse"
            className="mt-4 inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to browsing
          </Link>
        </div>
      </FadeIn>
    );
  }

  const vendor = data?.vendor;
  const products = data?.products?.content ?? [];
  const pageResponse = data?.products;

  return (
    <FadeIn className="max-w-7xl mx-auto px-4 py-8">
      {/* Vendor Profile Section */}
      {isPending ? (
        <div className="mb-8 animate-pulse space-y-4">
          <div className="h-8 w-1/4 bg-slate-200 rounded" aria-hidden="true" />
          <div className="h-4 w-1/2 bg-slate-200 rounded" aria-hidden="true" />
          <div className="h-64 w-full bg-slate-200 rounded-xl" aria-hidden="true" />
        </div>
      ) : isError ? (
        <div className="text-center py-12 border-2 border-dashed border-red-300 rounded-xl bg-red-50" role="alert">
          <svg className="mx-auto h-12 w-12 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77-1.333.192 3 1.732 3z" />
          </svg>
          <h2 className="mt-4 text-lg font-medium text-red-900">
            {error && error.message && error.message.includes("404")
              ? "Storefront not found"
              : "Could not load storefront"}
          </h2>
          <p className="mt-1 text-red-700">
            {error && error.message && error.message.includes("404")
              ? "This storefront does not exist or is not currently available."
              : "Something went wrong. Please try again."}
          </p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-4 inline-flex items-center gap-2 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Try again
          </button>
        </div>
      ) : vendor ? (
        <>
          <div className="mb-8 rounded-xl border border-slate-200 bg-white p-6 md:p-8">
            <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-6">
              <div className="flex-1 min-w-0">
                {vendor.logoUrl && (
                  <div className="mb-4">
                    <img
                      src={vendor.logoUrl}
                      alt={`${vendor.businessName} logo`}
                      className="h-20 w-auto max-w-full object-contain rounded-lg"
                      loading="lazy"
                    />
                  </div>
                )}
                <h1 className="text-2xl font-semibold text-slate-900 truncate">
                  {vendor.businessName}
                </h1>
                {vendor.description && (
                  <p className="mt-2 text-slate-600">{vendor.description}</p>
                )}

                <div className="mt-4 flex flex-wrap items-center gap-4 text-sm text-slate-600">
                  <span className="flex items-center gap-1">
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    {vendor.city}, {vendor.area} {vendor.pincode}
                  </span>
                  {vendor.deliveryRadiusKm !== null && vendor.deliveryRadiusKm !== undefined && (
                    <span className="flex items-center gap-1">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      Delivers within {vendor.deliveryRadiusKm.toFixed(1)} km
                    </span>
                  )}
                </div>

                {(vendor.avgRating !== null && vendor.reviewCount !== null && vendor.reviewCount > 0) && (
                  <div className="mt-4 flex items-center gap-3">
                    <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
                      {vendor.avgRating.toFixed(1)} ({vendor.reviewCount} review{vendor.reviewCount === 1 ? "" : "s"})
                    </span>
                  </div>
                )}

                {vendor.acceptingOrders === false && (
                  <div className="mt-4 inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-sm font-medium text-slate-600">
                    <svg className="h-4 w-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77-1.333.192 3 1.732 3z" />
                    </svg>
                    Not accepting orders at this time
                  </div>
                )}
              </div>

              <div className="flex flex-col items-end gap-4 shrink-0">
                {vendor.minOrderAmount !== null && vendor.minOrderAmount > 0 && (
                  <div className="text-right">
                    <p className="text-xs text-slate-500">Minimum order</p>
                    <p className="font-semibold text-slate-900">{formatMoney(vendor.minOrderAmount)}</p>
                  </div>
                )}
                <div className="text-right">
                  <p className="text-xs text-slate-500">Prep time</p>
                  <p className="font-semibold text-slate-900">
                    {vendor.prepTimeMinutes !== null ? `${vendor.prepTimeMinutes} min` : "--"}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-slate-500">Delivery from</p>
                  <p className="font-semibold text-slate-900">
                    {vendor.baseDeliveryFee !== null ? formatMoney(vendor.baseDeliveryFee) : "--"}
                  </p>
                </div>
              </div>
            </div>

            {/* Hours */}
            {vendor.hours && vendor.hours.length > 0 && (
              <div className="mt-6 pt-6 border-t border-slate-200">
                <h3 className="text-sm font-medium text-slate-900 mb-3">Operating hours</h3>
                <div className="grid grid-cols-7 gap-2 text-sm">
                  {["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].map((day) => {
                    const hours = vendor.hours!.find((h) => h.weekday === day);
                    const isClosed = hours?.closed ?? true;
                    const label = WEEKDAY_LABELS[day] ?? day.slice(0, 3);
                    return (
                      <div
                        key={day}
                        className={`flex flex-col items-center gap-1 p-2 rounded-md ${
                          isClosed ? "bg-slate-50 text-slate-400" : "bg-brand-50 text-brand-900"
                        }`}
                      >
                        <span className="font-medium">{label}</span>
                        <span className={isClosed ? "line-through" : ""}>
                          {isClosed
                            ? "Closed"
                            : `${hours?.openTime?.slice(0, 5) ?? "--"} - ${hours?.closeTime?.slice(0, 5) ?? "--"}`}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          {/* Products Section */}
          <div className="mb-8">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-slate-900">Products</h2>
              {pageResponse && pageResponse.totalElements > 0 && (
                <p className="text-sm text-slate-600">
                  {pageResponse.totalElements} product{pageResponse.totalElements === 1 ? "" : "s"} * Page {pageResponse.page + 1} of {Math.max(pageResponse.totalPages, 1)}
                </p>
              )}
            </div>

            {isPending ? (
              <ProductCardSkeletonGrid count={4} />
            ) : products.length === 0 ? (
              <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50">
                <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <h3 className="mt-4 text-lg font-medium text-slate-900">No products available</h3>
                <p className="mt-1 text-slate-600">
                  This florist hasn't listed any active products yet.
                </p>
              </div>
            ) : (
              <>
                <AnimatedList className="grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
                  {products.map((product) => (
                    <StorefrontProductCard key={product.id} product={toStorefrontProductCardView(product)} onClick={() => openProductDetail(toStorefrontProductCardView(product))} />
                  ))}
                </AnimatedList>

                {pageResponse && !pageResponse.empty && (
                  <Pagination<typeof products[0]>
                    page={pageResponse}
                    onPageChange={handlePageChange}
                    disabled={isFetching}
                  />
                )}
              
              {/* Product Detail Modal */}
              <ProductDetailModal
                product={selectedProduct}
                onClose={closeProductDetail}
              />
            </>
          )}
          </div>
        </>
      ) : (
        <div className="text-center py-12 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50">
          <svg className="mx-auto h-12 w-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <h3 className="mt-4 text-lg font-medium text-slate-900">Storefront unavailable</h3>
          <p className="mt-1 text-slate-600">This vendor's storefront could not be loaded.</p>
        </div>
      )}
    </FadeIn>
  );
}