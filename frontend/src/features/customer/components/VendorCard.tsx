import { motion } from "framer-motion";
import { formatMoney } from "@/features/vendor/format";
import { cardVariants, distances } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import type { VendorCardView } from "@/features/customer/types";

interface VendorCardProps {
  vendor: VendorCardView;
}

const CARD_CLASS = "flex flex-col h-full bg-white rounded-xl border border-slate-200 shadow-card p-4 transition-shadow duration-micro ease-standard hover:shadow-card-hover";

const BADGE_CLASS = "inline-flex items-center rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800";

/**
 * Vendor card for the customer browse page.
 *
 * Displays all required fields:
 * - Business name and service area
 * - Distance
 * - Delivery fee in INR
 * - Preparation time
 * - Minimum order in INR
 * - Rating and review count ("New" when no rating)
 *
 * Reuses `formatMoney` from the vendor slice for INR formatting.
 * Uses `AnimatedCard` motion pattern for entry and hover lift.
 */
export function VendorCard({ vendor }: VendorCardProps) {
  const reduceMotion = usePrefersReducedMotion();

  const ratingLabel = vendor.avgRating === null
    ? "New"
    : `${vendor.avgRating.toFixed(1)} (${vendor.reviewCount} review${vendor.reviewCount === 1 ? "" : "s"})`;

  const ratingBadgeClass = vendor.avgRating === null
    ? "inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600"
    : "inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800";

  const distanceText = vendor.distanceKm < 1
    ? `${Math.round(vendor.distanceKm * 1000)} m`
    : `${vendor.distanceKm.toFixed(1)} km`;

  const prepTimeText = vendor.prepTimeMinutes
    ? `${vendor.prepTimeMinutes} min prep`
    : "—";

  const minOrderText = vendor.minOrderAmount && vendor.minOrderAmount > 0
    ? `Min. order ${formatMoney(vendor.minOrderAmount)}`
    : "No minimum order";

  const acceptingText = vendor.acceptingOrders
    ? "Accepting orders"
    : "Not accepting orders";

  const acceptingClass = vendor.acceptingOrders
    ? "inline-flex items-center rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800"
    : "inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600";

  return (
    <motion.article
      className={CARD_CLASS}
      variants={cardVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
      whileHover={reduceMotion ? undefined : "hover"}
      whileTap={reduceMotion ? undefined : { scale: distances.press }}
    >
      <div className="flex flex-col flex-1">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="min-w-0 flex-1">
            <h3 className="text-lg font-semibold text-slate-900 truncate">
              {vendor.businessName}
            </h3>
            <p className="mt-0.5 text-sm text-slate-600">
              {vendor.area}, {vendor.pincode}
            </p>
          </div>
          <div className="flex flex-col items-end gap-1 shrink-0">
            <span className={BADGE_CLASS}>{distanceText}</span>
            <span className={ratingBadgeClass}>{ratingLabel}</span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600 mb-3">
          <span className="flex items-center gap-1">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 18.657A8 8 0 016.343 7.343S7 9 9 10c0-2 .5-5 2.986-7C14 2 16.09 5.777 17.656 7.343A7.975 7.975 0 0120 12a7.975 7.975 0 01-2.343 6.657z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            {formatMoney(vendor.estimatedDeliveryFee)}
          </span>
          <span className="flex items-center gap-1">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {prepTimeText}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
          <span className={acceptingClass}>{acceptingText}</span>
          <span className="text-slate-500">{minOrderText}</span>
        </div>

        <div className="mt-auto pt-3 border-t border-slate-100">
          <p className="text-xs text-slate-500">
            Delivery within {vendor.distanceKm.toFixed(1)} km radius
          </p>
        </div>
      </div>
    </motion.article>
  );
}