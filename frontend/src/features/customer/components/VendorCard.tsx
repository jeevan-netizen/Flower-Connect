import { Link } from "react-router-dom";
import type { DiscoveryVendor } from "@/features/customer/types";
import {
  formatCustomerRating,
  formatDistanceKm,
  formatMoney,
  formatPrepTime,
} from "@/features/customer/format";
import { AnimatedCard } from "@/motion/AnimatedCard";
import { PRESSABLE } from "@/motion/pressable";

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
    <AnimatedCard className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <article className="flex h-full flex-col">
        <h3 className="text-base font-semibold text-slate-900">{vendor.businessName}</h3>
        <p className="mt-1 text-sm text-slate-600">
          {vendor.area}, {vendor.city} {vendor.pincode}
        </p>

        {vendor.description && (
          <p className="mt-2 line-clamp-2 text-sm text-slate-600">{vendor.description}</p>
        )}

        <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Distance</dt>
            <dd className="font-medium text-slate-900">{formatDistanceKm(vendor.distanceKm)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Delivery</dt>
            <dd className="font-medium text-slate-900">
              {formatMoney(vendor.estimatedDeliveryFee)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Prep time</dt>
            <dd className="font-medium text-slate-900">{formatPrepTime(vendor.prepTimeMinutes)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">Minimum order</dt>
            <dd className="font-medium text-slate-900">{formatMoney(vendor.minOrderAmount)}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Rating</dt>
            <dd className="font-medium text-slate-900">
              {formatCustomerRating(vendor.avgRating, vendor.reviewCount)}
            </dd>
          </div>
        </dl>

        <div className="mt-5 pt-2">
          <Link
            to={`/vendors/${vendor.id}`}
            className={`inline-flex items-center justify-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 ${PRESSABLE}`}
          >
            View shop
          </Link>
        </div>
      </article>
    </AnimatedCard>
  );
}
