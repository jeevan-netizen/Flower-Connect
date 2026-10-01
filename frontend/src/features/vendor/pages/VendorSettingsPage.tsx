import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useUpdateVendorProfile, useVendorProfile } from "@/features/vendor/queries";
import { buildProfileUpdateRequest, type VendorProfile } from "@/features/vendor/types";
import {
  optionalDecimalField,
  requiredDecimalField,
  requiredIntegerField,
  toNumber,
  toNumberOrNull,
} from "@/features/vendor/form-schema";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import {
  CheckboxField,
  FormErrorSummary,
  NumberField,
  SubmitButton,
  SuccessMessage,
} from "@/features/vendor/components/FormFields";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { formatStatus } from "@/features/vendor/format";

/**
 * Delivery settings (plan tasks 2.3 / 2.9).
 *
 * Every bound mirrors `VendorProfileUpdateRequest`:
 *   delivery_radius_km  > 0, 3 integer digits, 2 fraction digits
 *   money fields        >= 0, 8 integer digits, 2 fraction digits
 *   prep / slot minutes 1..1440, max orders per slot 1..10000
 *
 * `serviceLocationId`, the stored coordinates and the vendor's own lat/lng copy
 * are not editable here: the location is the sole source of coordinates
 * (D-4), and this page sends no latitude/longitude at all.
 */
const settingsSchema = z.object({
  deliveryRadiusKm: requiredDecimalField("Delivery radius", {
    integerDigits: 3,
    fractionDigits: 2,
    min: 0,
    minExclusive: true,
  }),
  minOrderAmount: requiredDecimalField("Minimum order amount", {
    integerDigits: 8,
    fractionDigits: 2,
    min: 0,
  }),
  baseDeliveryFee: requiredDecimalField("Base delivery fee", {
    integerDigits: 8,
    fractionDigits: 2,
    min: 0,
  }),
  perKmFee: requiredDecimalField("Per km fee", {
    integerDigits: 8,
    fractionDigits: 2,
    min: 0,
  }),
  freeDeliveryAbove: optionalDecimalField("Free delivery threshold", {
    integerDigits: 8,
    fractionDigits: 2,
    min: 0,
  }),
  prepTimeMinutes: requiredIntegerField("Preparation time", 1, 1440),
  slotDurationMinutes: requiredIntegerField("Slot duration", 1, 1440),
  maxOrdersPerSlot: requiredIntegerField("Max orders per slot", 1, 10000),
  acceptingOrders: z.boolean(),
});

type SettingsFormValues = z.infer<typeof settingsSchema>;

const SETTINGS_FIELDS = [
  "deliveryRadiusKm",
  "minOrderAmount",
  "baseDeliveryFee",
  "perKmFee",
  "freeDeliveryAbove",
  "prepTimeMinutes",
  "slotDurationMinutes",
  "maxOrdersPerSlot",
] as const satisfies readonly (keyof SettingsFormValues)[];

function isSettingsField(field: string): field is keyof SettingsFormValues {
  return (SETTINGS_FIELDS as readonly string[]).includes(field);
}

function toFormValues(profile: VendorProfile): SettingsFormValues {
  return {
    deliveryRadiusKm: String(profile.deliveryRadiusKm),
    minOrderAmount: String(profile.minOrderAmount),
    baseDeliveryFee: String(profile.baseDeliveryFee),
    perKmFee: String(profile.perKmFee),
    freeDeliveryAbove: profile.freeDeliveryAbove === null ? "" : String(profile.freeDeliveryAbove),
    prepTimeMinutes: String(profile.prepTimeMinutes),
    slotDurationMinutes: String(profile.slotDurationMinutes),
    maxOrdersPerSlot: String(profile.maxOrdersPerSlot),
    acceptingOrders: profile.acceptingOrders,
  };
}

export function VendorSettingsPage() {
  const { data: profile, isPending, error, refetch } = useVendorProfile();
  const updateProfile = useUpdateVendorProfile();
  const [success, setSuccess] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SettingsFormValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      deliveryRadiusKm: "",
      minOrderAmount: "",
      baseDeliveryFee: "",
      perKmFee: "",
      freeDeliveryAbove: "",
      prepTimeMinutes: "",
      slotDurationMinutes: "",
      maxOrdersPerSlot: "",
      acceptingOrders: true,
    },
  });

  useEffect(() => {
    if (profile) {
      reset(toFormValues(profile));
    }
  }, [profile, reset]);

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your delivery settings...
      </div>
    );
  }

  if (error || !profile) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setSuccess(null);
    setSubmitError(null);

    try {
      await updateProfile.mutateAsync(
        buildProfileUpdateRequest(profile, {
          deliveryRadiusKm: toNumber(values.deliveryRadiusKm),
          minOrderAmount: toNumber(values.minOrderAmount),
          baseDeliveryFee: toNumber(values.baseDeliveryFee),
          perKmFee: toNumber(values.perKmFee),
          freeDeliveryAbove: toNumberOrNull(values.freeDeliveryAbove),
          prepTimeMinutes: toNumber(values.prepTimeMinutes),
          slotDurationMinutes: toNumber(values.slotDurationMinutes),
          maxOrdersPerSlot: toNumber(values.maxOrdersPerSlot),
          acceptingOrders: values.acceptingOrders,
        }),
      );
      setSuccess("Delivery settings saved.");
    } catch (mutationError) {
      const info = toApiError(mutationError);
      setSubmitError(info);
      Object.entries(info.validation).forEach(([field, message]) => {
        if (isSettingsField(field)) {
          setError(field, { type: "server", message });
        }
      });
    }
  });

  return (
    <div className="space-y-6">
      <PageHeading
        title="Delivery settings"
        description={`Applies while your vendor account is ${formatStatus(profile.status).toLowerCase()}.`}
      />

      <Card>
        <form className="space-y-5" onSubmit={onSubmit} noValidate>
          <FormErrorSummary
            message={
              submitError && Object.keys(submitError.validation).length === 0
                ? submitError.message
                : submitError
                  ? "Some fields need attention."
                  : null
            }
          />
          <SuccessMessage message={success} />

          <NumberField
            label="Delivery radius (km)"
            step="0.01"
            min="0"
            hint="How far from your area centroid you deliver. No delivery zones in v1."
            error={errors.deliveryRadiusKm?.message}
            registration={register("deliveryRadiusKm")}
          />

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <NumberField
              label="Minimum order amount (₹)"
              min="0"
              error={errors.minOrderAmount?.message}
              registration={register("minOrderAmount")}
            />
            <NumberField
              label="Base delivery fee (₹)"
              min="0"
              error={errors.baseDeliveryFee?.message}
              registration={register("baseDeliveryFee")}
            />
            <NumberField
              label="Per km fee (₹)"
              min="0"
              error={errors.perKmFee?.message}
              registration={register("perKmFee")}
            />
            <NumberField
              label="Free delivery above (₹)"
              min="0"
              hint="Leave blank to disable free delivery."
              error={errors.freeDeliveryAbove?.message}
              registration={register("freeDeliveryAbove")}
            />
          </div>

          <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
            <NumberField
              label="Preparation time (minutes)"
              step="1"
              min="1"
              error={errors.prepTimeMinutes?.message}
              registration={register("prepTimeMinutes")}
            />
            <NumberField
              label="Slot duration (minutes)"
              step="1"
              min="1"
              error={errors.slotDurationMinutes?.message}
              registration={register("slotDurationMinutes")}
            />
            <NumberField
              label="Max orders per slot"
              step="1"
              min="1"
              error={errors.maxOrdersPerSlot?.message}
              registration={register("maxOrdersPerSlot")}
            />
          </div>

          <CheckboxField
            label="Accepting orders"
            hint="Turn this off to pause new orders without suspending your account."
            registration={register("acceptingOrders")}
          />

          <SubmitButton
            isSubmitting={isSubmitting || updateProfile.isPending}
            idleLabel="Save delivery settings"
            busyLabel="Saving..."
          />
        </form>
      </Card>
    </div>
  );
}
