import { useState } from "react";
import { Link } from "react-router-dom";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRegisterVendor, useServiceLocations } from "@/features/vendor-registration/queries";
import {
  isVendorRegisterField,
  vendorRegisterSchema,
  type VendorRegisterForm,
} from "@/features/vendor-registration/form-schema";
import {
  buildVendorRegisterRequest,
  hasNoServiceLocations,
  toServiceLocationOptions,
  VENDOR_REGISTER_DEFAULTS,
  type VendorRegistrationResult,
} from "@/features/vendor-registration/types";
import { LocationPicker } from "@/features/vendor-registration/components/LocationPicker";
import { TextInput } from "@/features/vendor-registration/components/TextInput";
import {
  FormErrorSummary,
  SubmitButton,
  SuccessMessage,
} from "@/features/vendor/components/FormFields";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

/**
 * The vendor entry point (plan task 1.7).
 *
 * Public page: `POST /api/v1/vendors/register` creates the FLORIST account and a
 * `PENDING_APPROVAL` profile in one transaction. It returns the profile, **not**
 * tokens, so this page deliberately never touches the auth store — it hands off to
 * the existing login page with `from: "/vendor"`, which lands the new florist on the
 * Task 2.9 vendor dashboard where the existing pending-approval banner explains the
 * wait. No approval state is evaluated here; the banner owns that.
 *
 * The form sends only the identity, business and location fields. Every delivery
 * setting is optional on the backend and falls back to its schema default, and the
 * vendor edits them on `/vendor/settings` and `/vendor/hours`, which are the same
 * profile resource (docs/decisions.md, D-15).
 */
export function VendorRegisterPage() {
  const {
    data: cities,
    isPending: locationsPending,
    error: locationsError,
    refetch: refetchLocations,
  } = useServiceLocations();
  const registerVendor = useRegisterVendor();

  const [registered, setRegistered] = useState<VendorRegistrationResult | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);

  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<VendorRegisterForm>({
    resolver: zodResolver(vendorRegisterSchema),
    defaultValues: VENDOR_REGISTER_DEFAULTS,
  });

  const options = toServiceLocationOptions(cities);
  const noServiceAreas = !locationsPending && !locationsError && hasNoServiceLocations(options);
  // The service area is a required field. Until the areas have loaded, after a failed
  // fetch, or for an empty region, the field is rendered but disabled — so the vendor
  // can see what registration requires instead of hitting an unexplained failure.
  const pickerUnavailable = locationsPending || locationsError !== null || noServiceAreas;
  const pickerHint = locationsPending
    ? "Loading the available service areas..."
    : locationsError
      ? "Service areas are unavailable right now."
      : noServiceAreas
        ? "Nothing to choose from yet."
        : undefined;

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);

    try {
      const result = await registerVendor.mutateAsync(buildVendorRegisterRequest(values));
      setRegistered(result);
      setRegisteredEmail(values.email.trim());
    } catch (error) {
      const info = toApiError(error);
      setSubmitError(info);
      // Surface per-field backend messages on the matching inputs. Unknown keys stay
      // in the summary banner.
      Object.entries(info.validation).forEach(([field, message]) => {
        if (isVendorRegisterField(field)) {
          setError(field, { type: "server", message });
        }
      });
    }
  });

  if (registered) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <PageHeading
          title="Application received"
          description="FlowerConnect reviews every new florist before it can list products."
        />

        <Card>
          <SuccessMessage
            message={`Thanks — ${registered.businessName} has been registered for ${registered.area}, ${registered.city} (${registered.pincode}).`}
          />
          <p className="mt-3 text-sm text-slate-600">
            Your profile is <span className="font-medium">{registered.status}</span> until an
            administrator approves it. Sign in now to follow your application in the vendor
            dashboard; listing products unlocks once you are approved.
          </p>

          <div className="mt-5 flex flex-wrap gap-3">
            <Link
              to="/login"
              state={{ registeredEmail, from: "/vendor" }}
              className="rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
            >
              Sign in to your vendor dashboard
            </Link>
            <button
              type="button"
              onClick={() => {
                setRegistered(null);
                setRegisteredEmail(null);
              }}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Register another shop
            </button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeading
        title="Register your flower shop"
        description="Create a florist account and tell us where you deliver. An administrator approves every new shop before it can list products."
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

          <fieldset className="space-y-5">
            <legend className="text-sm font-semibold text-slate-900">Your account</legend>
            <TextInput
              label="Full name"
              autoComplete="name"
              error={errors.fullName?.message}
              registration={register("fullName")}
            />
            <TextInput
              label="Email address"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              registration={register("email")}
            />
            <TextInput
              label="Phone number"
              type="tel"
              autoComplete="tel"
              hint="Optional. 7-15 digits, with an optional leading +."
              error={errors.phone?.message}
              registration={register("phone")}
            />
            <TextInput
              label="Password"
              type="password"
              autoComplete="new-password"
              error={errors.password?.message}
              registration={register("password")}
            />
            <TextInput
              label="Confirm password"
              type="password"
              autoComplete="new-password"
              error={errors.confirmPassword?.message}
              registration={register("confirmPassword")}
            />
          </fieldset>

          <fieldset className="space-y-5 border-t border-slate-200 pt-5">
            <legend className="text-sm font-semibold text-slate-900">Your business</legend>
            <TextInput
              label="Business name"
              error={errors.businessName?.message}
              registration={register("businessName")}
            />
            <TextInput
              label="Description"
              multiline
              hint="Optional. Shown to customers browsing nearby shops."
              error={errors.description?.message}
              registration={register("description")}
            />
          </fieldset>

          <fieldset className="space-y-5 border-t border-slate-200 pt-5">
            <legend className="text-sm font-semibold text-slate-900">Where you deliver</legend>
            <TextInput
              label="Address line 1"
              autoComplete="address-line1"
              error={errors.addressLine1?.message}
              registration={register("addressLine1")}
            />
            <TextInput
              label="Address line 2"
              autoComplete="address-line2"
              hint="Optional."
              error={errors.addressLine2?.message}
              registration={register("addressLine2")}
            />

            {locationsPending && <p className="text-sm text-slate-600">Loading service areas...</p>}

            {locationsError && (
              <div className="rounded-md bg-red-50 p-3 text-sm text-red-700" role="alert">
                <p>Could not load the service areas.</p>
                <button
                  type="button"
                  onClick={() => void refetchLocations()}
                  className="mt-2 font-medium underline"
                >
                  Try again
                </button>
              </div>
            )}

            {noServiceAreas && (
              <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-800" role="alert">
                No service areas are configured yet, so registration is unavailable. Please
                contact FlowerConnect support.
              </p>
            )}

            <Controller
              control={control}
              name="serviceLocationId"
              render={({ field }) => (
                <LocationPicker
                  cities={cities}
                  options={options}
                  value={field.value}
                  hint={pickerHint}
                  disabled={pickerUnavailable || registerVendor.isPending}
                  error={errors.serviceLocationId?.message}
                  onChange={field.onChange}
                />
              )}
            />
          </fieldset>

          <SubmitButton
            isSubmitting={isSubmitting || registerVendor.isPending}
            idleLabel="Submit application"
            busyLabel="Submitting..."
          />

          {/*
            The button is not disabled when the areas are unavailable: `SubmitButton`
            derives its label from the same flag, so blocking it would leave the form
            reading "Submitting..." forever. Submitting instead fails the required
            service-area rule, and the note below says why the field is missing.
          */}
          {pickerUnavailable && (
            <p className="text-xs text-slate-500">
              Registration needs the service area list, so it cannot be completed until the areas
              load.
            </p>
          )}
        </form>
      </Card>

      <p className="text-center text-sm text-slate-600">
        Already registered?{" "}
        <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
          Sign in
        </Link>{" "}
        or{" "}
        <Link to="/register" className="font-medium text-brand-600 hover:text-brand-700">
          create a customer account
        </Link>
        .
      </p>
    </div>
  );
}