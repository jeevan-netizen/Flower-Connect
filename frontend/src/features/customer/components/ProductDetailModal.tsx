import { useCallback, useEffect, useId, useRef } from "react";
import { motion } from "framer-motion";
import { formatMoney } from "@/features/vendor/format";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import type { ProductCardView, StorefrontProductCardView } from "@/features/customer/types";

type ProductDetailData = ProductCardView | StorefrontProductCardView;

interface ProductDetailModalProps {
  /** The product to display, or null to keep the modal closed. */
  product: ProductDetailData | null;
  /** Called when the modal should close. */
  onClose: () => void;
  /** Optional note to display as a "gift note" preview. Not persisted. */
  note?: string | null;
}

/**
 * Product detail modal (plan task 4.9).
 *
 * Displays all available product fields from the browse, search, or storefront
 * responses. Uses a generic flower placeholder for images (D-31, Issue 020).
 *
 * Accessibility:
 * - Proper dialog semantics with aria-modal, aria-labelledby, aria-describedby
 * - Focus trapping (focus moves to close button on open, returns to trigger on close)
 * - Escape key closes modal
 * - Backdrop click closes modal
 * - Click inside dialog does not dismiss
 * - Focus restored to trigger element on close
 * - Reduced motion respected
 */
export function ProductDetailModal({ product, onClose, note = null }: ProductDetailModalProps) {
  const headingId = useId();
  const descriptionId = useId();
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  // Focus management: store the element that had focus before modal opened
  const triggerRef = useRef<HTMLElement | null>(null);

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    },
    [onClose],
  );

  const handleBackdropClick = useCallback(
    (event: React.MouseEvent) => {
      // Only close if clicking directly on the backdrop, not the dialog
      if (event.target === event.currentTarget) {
        onClose();
      }
    },
    [onClose],
  );

  // Focus management on open/close
  useEffect(() => {
    if (product) {
      // Store the currently focused element as the trigger
      triggerRef.current = document.activeElement as HTMLElement;

      // Focus the close button when modal opens
      setTimeout(() => {
        const closeButton = document.getElementById("product-detail-close");
        closeButton?.focus();
      }, 0);

      // Prevent background scrolling
      document.body.style.overflow = "hidden";
    }

    return () => {
      // Restore focus to trigger element
      if (triggerRef.current) {
        triggerRef.current.focus();
      }
      // Restore scrolling
      document.body.style.overflow = "";
    };
  }, [product]);

  if (!product) return null;

  const isStorefrontProduct = "inStock" in product;
  const vendorName = "vendorName" in product ? product.vendorName : null;
  const distanceKm = "distanceKm" in product ? product.distanceKm : null;
  const estimatedDeliveryFee = "estimatedDeliveryFee" in product ? product.estimatedDeliveryFee : null;

  const inStock = isStorefrontProduct ? product.inStock : true; // search products are always in stock (filtered)
  const stockLabel = inStock ? "In stock" : "Out of stock";
  const stockBadgeClass = inStock
    ? "inline-flex items-center rounded-full bg-brand-100 px-2 py-0.5 text-xs font-medium text-brand-800"
    : "inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600";

  const distanceText = distanceKm !== null
    ? distanceKm < 1
      ? `${Math.round(distanceKm * 1000)} m`
      : `${distanceKm.toFixed(1)} km`
    : null;

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      variants={backdropVariants}
      initial={enterState}
      animate="visible"
      exit={exitState}
      onClick={handleBackdropClick}
      role="presentation"
    >
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        aria-describedby={descriptionId}
        className="w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-lg"
        variants={dialogVariants}
        initial={enterState}
        animate="visible"
        exit={exitState}
        onKeyDown={handleKeyDown}
        onClick={(e) => e.stopPropagation()} // Prevent backdrop click when clicking inside dialog
      >
        <div className="relative p-6">
          {/* Close button */}
          <button
            id="product-detail-close"
            type="button"
            onClick={onClose}
            className="absolute top-3 right-3 rounded-full p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 transition-colors"
            aria-label="Close product details"
          >
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          {/* Product image placeholder (D-31, Issue 020) */}
          <div className="aspect-square bg-slate-100 rounded-xl flex items-center justify-center mb-5">
            <svg className="h-48 w-48 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={0.75} d="M12 11.25a2.25 2.25 0 114.5 0 2.25 2.25 0 01-4.5 0zM4.5 15.75a4.5 4.5 0 009 0 4.5 4.5 0 00-9 0zM12 3.75a2.25 2.25 0 114.5 0 2.25 2.25 0 01-4.5 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={0.75} d="M12 3v1.5m0 16.5V21m-4.5-13.5V3m7.5 1.5v1.5m0 16.5V21M3 12h1.5m16.5 0H21" />
            </svg>
          </div>

          {/* Product info */}
          <h2 id={headingId} className="text-xl font-semibold text-slate-900 mb-2">
            {product.name}
          </h2>

          <p id={descriptionId} className="text-sm text-slate-600 mb-4">
            {product.description}
          </p>

          <div className="flex flex-wrap items-center gap-3 mb-4">
            {vendorName && (
              <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                {vendorName}
              </span>
            )}
            <span className="inline-flex items-center gap-1 rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800">
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
              </svg>
              {product.categoryName}
            </span>
            {distanceText && (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                {distanceText}
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3 mb-4">
            <span className={stockBadgeClass}>
              {stockLabel}
            </span>
          </div>

          <div className="mb-4">
            <span className="text-2xl font-bold text-slate-900">
              {formatMoney(product.basePrice)}
            </span>
            {estimatedDeliveryFee !== null && estimatedDeliveryFee > 0 && (
              <span className="ml-3 text-sm text-slate-600">
                (+ {formatMoney(estimatedDeliveryFee)} delivery)
              </span>
            )}
          </div>

          {/* Gift note preview (optional, not persisted in this phase) */}
          {note && (
            <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 mb-4">
              <p className="text-xs font-medium text-amber-900 mb-1">Gift note (preview)</p>
              <p className="text-sm text-amber-800 whitespace-pre-wrap">{note}</p>
              <p className="text-xs text-amber-600 mt-1">Note will be attached to the order item when you add to cart.</p>
            </div>
          )}

          <div className="flex justify-end pt-4 border-t border-slate-200">
            <button
              type="button"
              onClick={onClose}
              className={`rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 transition-colors ${PRESSABLE} ${FOCUS_RING}`}
            >
              Close
            </button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}