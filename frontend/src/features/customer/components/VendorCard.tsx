import { Link } from "react-router-dom";
import type { DiscoveryVendor } from "@/features/customer/types";
import {
  formatDistanceKm,
  formatMoney,
  formatPrepTime,
} from "@/features/customer/format";
import { StarRating } from "@/features/review/components/StarRating";
import { AnimatedCard } from "@/motion/AnimatedCard";
import { BUTTON_PRIMARY, CARD } from "@/shared/ui/classes";

interface VendorCardProps {
  vendor: DiscoveryVendor;
}

/**
 * One florist on the browse page (plan task 4.6).
 *
 * Everything shown here is computed by the server: the distance from the
 * customer's area, the estimated delivery fee and the rating. The card never
 * derives a distance or a price in the browser, and it never filters the list
 * — the API already returned only florists that deliver to the chosen area and
 * accept orders.
 */
export function VendorCard({ vendor }: VendorCardProps) {
  return (
    <AnimatedCard className={`${CARD} flex p-5`}>
      <article className="flex h-full w-full flex-col">
        <h3 className="font-display text-xl text-bolder-text">{vendor.businessName}</h3>
        <p className="mt-1 text-sm text-bolder-muted">
          {vendor.area}, {vendor.city} {vendor.pincode}
        </p>

        {vendor.description && (
          <p className="mt-3 line-clamp-2 text-sm leading-relaxed text-bolder-muted">
            {vendor.description}
          </p>
        )}

        <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
          <div>
            <dt className="text-xs text-bolder-muted">Distance</dt>
            <dd className="mt-0.5 font-medium text-bolder-text">
              {formatDistanceKm(vendor.distanceKm)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-bolder-muted">Delivery</dt>
            <dd className="mt-0.5 font-medium text-bolder-text">
              {formatMoney(vendor.estimatedDeliveryFee)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-bolder-muted">Prep time</dt>
            <dd className="mt-0.5 font-medium text-bolder-text">
              {formatPrepTime(vendor.prepTimeMinutes)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-bolder-muted">Minimum order</dt>
            <dd className="mt-0.5 font-medium text-bolder-text">
              {formatMoney(vendor.minOrderAmount)}
            </dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs text-bolder-muted">Rating</dt>
            <dd className="mt-0.5 font-medium text-bolder-text">
              <StarRating rating={vendor.avgRating} reviewCount={vendor.reviewCount} />
            </dd>
          </div>
        </dl>

        <div className="mt-auto pt-5">
          <Link to={`/vendors/${vendor.id}`} className={BUTTON_PRIMARY}>
            View shop
          </Link>
        </div>
      </article>
    </AnimatedCard>
  );
}
