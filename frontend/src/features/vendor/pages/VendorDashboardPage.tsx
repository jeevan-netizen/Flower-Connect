import { Link } from "react-router-dom";
import { useVendorProfile } from "@/features/vendor/queries";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { ReadOnlyRow } from "@/features/vendor/components/FormFields";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { VendorStatusBadge } from "@/features/vendor/components/VendorStatusBanner";
import {
  countOpenDays,
  formatDecimal,
  formatMoney,
  formatRating,
  formatTime,
  weekdayLabel,
} from "@/features/vendor/format";

/**
 * Vendor dashboard (plan task 2.9: "sidebar navigation, stats cards").
 *
 * There is no dedicated dashboard endpoint in Phase 2, so every figure here is
 * composed from the single `GET /api/v1/vendors/profile` response — no extra
 * requests, and nothing invented that the backend does not already expose.
 */
export function VendorDashboardPage() {
  const { data: profile, isPending, error, refetch } = useVendorProfile();

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your dashboard...
      </div>
    );
  }

  if (error || !profile) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  const openDays = countOpenDays(profile.hours);
  const nextOpenDay = (profile.hours ?? []).find((day) => !day.closed);

  return (
    <div className="space-y-6">
      <PageHeading
        title={profile.businessName}
        description={`${profile.city}, ${profile.area} — ${profile.pincode}`}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Approval status"
          value={<VendorStatusBadge status={profile.status} />}
          hint={profile.status === "APPROVED" ? "Visible to customers" : "Not discoverable yet"}
        />
        <StatCard
          label="Delivery radius"
          value={formatDecimal(profile.deliveryRadiusKm, " km")}
          hint="Area-centre based (D-4)"
        />
        <StatCard
          label="Preparation time"
          value={`${profile.prepTimeMinutes} min`}
          hint={`${profile.slotDurationMinutes} min slots`}
        />
        <StatCard
          label="Capacity"
          value={`${profile.maxOrdersPerSlot} / slot`}
          hint={profile.acceptingOrders ? "Accepting orders" : "Not accepting orders"}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card title="Delivery settings">
          <dl>
            <ReadOnlyRow label="Minimum order amount" value={formatMoney(profile.minOrderAmount)} />
            <ReadOnlyRow label="Base delivery fee" value={formatMoney(profile.baseDeliveryFee)} />
            <ReadOnlyRow label="Per km fee" value={formatMoney(profile.perKmFee)} />
            <ReadOnlyRow label="Free delivery above" value={formatMoney(profile.freeDeliveryAbove)} />
            <ReadOnlyRow label="Delivery radius" value={formatDecimal(profile.deliveryRadiusKm, " km")} />
          </dl>
          <Link to="/vendor/settings" className="mt-4 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
            Edit delivery settings
          </Link>
        </Card>

        <Card title="Operating hours">
          <dl>
            <ReadOnlyRow label="Days open" value={`${openDays} of 7`} />
            <ReadOnlyRow
              label={nextOpenDay ? `Next open day (${weekdayLabel(nextOpenDay.weekday)})` : "Next open day"}
              value={
                nextOpenDay
                  ? `${formatTime(nextOpenDay.openTime)} – ${formatTime(nextOpenDay.closeTime)}`
                  : "No open days configured"
              }
            />
          </dl>
          <Link to="/vendor/hours" className="mt-4 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
            Edit operating hours
          </Link>
        </Card>

        <Card title="Business">
          <dl>
            <ReadOnlyRow label="Business name" value={profile.businessName} />
            <ReadOnlyRow label="Owner email" value={profile.ownerEmail} />
            <ReadOnlyRow label="Service location" value={`${profile.area}, ${profile.city}`} />
            <ReadOnlyRow label="Rating" value={formatRating(profile.avgRating, profile.reviewCount)} />
          </dl>
          <Link to="/vendor/profile" className="mt-4 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
            Edit profile
          </Link>
        </Card>

        <Card title="Profile completeness">
          <p className="text-sm text-slate-700">
            Everything a customer sees about your shop is editable from the profile and settings
            pages. Commission rate, approval status and ratings are set by FlowerConnect.
          </p>
          <ul className="mt-3 space-y-1 text-sm text-slate-600">
            <li>✓ Business name and description</li>
            <li>✓ Address and service location</li>
            <li>✓ Delivery settings and operating hours</li>
          </ul>
        </Card>
      </div>
    </div>
  );
}
