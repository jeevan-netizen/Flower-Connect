import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useUpdateVendorProfile, useVendorProfile } from "@/features/vendor/queries";
import { buildProfileUpdateRequest, type VendorProfile } from "@/features/vendor/types";
import {
  logoUrlField,
  optionalTextField,
  requiredTextField,
  toTextOrNull,
} from "@/features/vendor/form-schema";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import {
  FormErrorSummary,
  ReadOnlyRow,
  SubmitButton,
  SuccessMessage,
  TextField,
} from "@/features/vendor/components/FormFields";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { formatDecimal, formatRating, formatStatus } from "@/features/vendor/format";

/**
 * Mirrors `VendorProfileUpdateRequest` for the fields this page owns. The
 * delivery-setting and hours fields live on their own pages but are part of the
 * same PUT, so this schema is deliberately narrower than the payload.
 */
const profileSchema = z.object({
  businessName: requiredTextField("Business name", 160),
  description: optionalTextField("Description", 1000),
  addressLine1: requiredTextField("Address line 1", 255),
  addressLine2: optionalTextField("Address line 2", 255),
  logoUrl: logoUrlField,
});

type ProfileFormValues = z.infer<typeof profileSchema>;

const PROFILE_FIELDS = [
  "businessName",
  "description",
  "addressLine1",
  "addressLine2",
  "logoUrl",
] as const satisfies readonly (keyof ProfileFormValues)[];

/** Narrows a backend validation key so it can be attached to a real input. */
function isProfileField(field: string): field is keyof ProfileFormValues {
  return (PROFILE_FIELDS as readonly string[]).includes(field);
}

function toFormValues(profile: VendorProfile): ProfileFormValues {
  return {
    businessName: profile.businessName,
    description: profile.description ?? "",
    addressLine1: profile.addressLine1,
    addressLine2: profile.addressLine2 ?? "",
    logoUrl: profile.logoUrl ?? "",
  };
}

export function VendorProfilePage() {
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
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      businessName: "",
      description: "",
      addressLine1: "",
      addressLine2: "",
      logoUrl: "",
    },
  });

  // The profile is the form's source of truth: re-seed whenever a refetch or a
  // successful save produces a new one.
  useEffect(() => {
    if (profile) {
      reset(toFormValues(profile));
    }
  }, [profile, reset]);

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your profile...
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
          businessName: values.businessName,
          description: toTextOrNull(values.description),
          addressLine1: values.addressLine1,
          addressLine2: toTextOrNull(values.addressLine2),
          logoUrl: toTextOrNull(values.logoUrl),
        }),
      );
      setSuccess("Profile saved.");
    } catch (mutationError) {
      const info = toApiError(mutationError);
      setSubmitError(info);
      // Surface per-field backend messages on the matching inputs. Unknown keys
      // (cross-field hours rules, for instance) stay in the summary banner.
      Object.entries(info.validation).forEach(([field, message]) => {
        if (isProfileField(field)) {
          setError(field, { type: "server", message });
        }
      });
    }
  });

  return (
    <div className="space-y-6">
      <PageHeading
        title="Vendor profile"
        description="Business details customers see. Approval status and rating are managed by FlowerConnect."
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

          <TextField
            label="Business name"
            error={errors.businessName?.message}
            registration={register("businessName")}
          />
          <TextField
            label="Description"
            multiline
            hint="Optional. Shown to customers browsing nearby shops."
            error={errors.description?.message}
            registration={register("description")}
          />
          <TextField
            label="Address line 1"
            error={errors.addressLine1?.message}
            registration={register("addressLine1")}
          />
          <TextField
            label="Address line 2"
            error={errors.addressLine2?.message}
            registration={register("addressLine2")}
          />
          <TextField
            label="Logo URL"
            hint="Optional. An absolute http(s) URL or a path served by FlowerConnect."
            error={errors.logoUrl?.message}
            registration={register("logoUrl")}
          />

          <SubmitButton
            isSubmitting={isSubmitting || updateProfile.isPending}
            idleLabel="Save profile"
            busyLabel="Saving..."
          />
        </form>
      </Card>

      <Card title="Managed by FlowerConnect">
        <p className="text-sm text-slate-600">
          These values come from the platform and cannot be edited here.
        </p>
        <dl className="mt-3">
          <ReadOnlyRow label="Approval status" value={formatStatus(profile.status)} />
          <ReadOnlyRow label="Owner email" value={profile.ownerEmail} />
          <ReadOnlyRow label="Service location" value={`${profile.area}, ${profile.city} (${profile.pincode})`} />
          <ReadOnlyRow
            label="Coordinates"
            value={`${formatDecimal(profile.latitude)}, ${formatDecimal(profile.longitude)} (area centroid)`}
          />
          <ReadOnlyRow label="Rating" value={formatRating(profile.avgRating, profile.reviewCount)} />
          <ReadOnlyRow label="Last updated" value={profile.updatedAt} />
        </dl>
      </Card>
    </div>
  );
}
