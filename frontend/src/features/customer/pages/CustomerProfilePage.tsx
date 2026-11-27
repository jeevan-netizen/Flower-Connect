import { useEffect, useId, useMemo, useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { updateCurrentUser } from "@/features/auth/api";
import {
  useAddresses,
  useCreateAddress,
  useDeleteAddress,
  useUpdateAddress,
  clearAddressCache,
} from "@/features/customer/address/queries";
import { useServiceLocations } from "@/features/location/queries";
import { toServiceLocationOptions } from "@/features/location/types";
import { toApiError } from "@/shared/lib/api-error";
import { useQueryClient } from "@tanstack/react-query";
import type { AddressResponse } from "@/features/checkout/types";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import {
  CheckboxField,
  FormErrorSummary,
  ReadOnlyRow,
  SubmitButton,
  SuccessMessage,
  TextField,
} from "@/features/vendor/components/FormFields";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PRESSABLE } from "@/motion/pressable";

/** Shape of the editable account fields. Mirrors `PATCH /api/v1/users/me`. */
const accountSchema = z.object({
  fullName: z.string().min(1, "Full name is required").max(128, "Full name must not exceed 128 characters"),
  phone: z
    .string()
    .max(20, "Phone must be at most 20 characters")
    .regex(/^\+?[0-9]{7,15}$/, "Phone must be a valid phone number")
    .optional()
    .default(""),
});

type AccountFormValues = z.infer<typeof accountSchema>;

/** Shape of an address create/update form. Mirrors `AddressRequest`. */
const addressSchema = z.object({
  label: z.string().min(1, "Label is required").max(128, "Label must not exceed 128 characters"),
  line1: z.string().min(1, "Address line 1 is required").max(255, "Address line 1 must not exceed 255 characters"),
  line2: z.string().max(255, "Address line 2 must not exceed 255 characters").optional().default(""),
  serviceLocationId: z.coerce.number().int().positive("Select a service area"),
  defaultAddress: z.boolean().optional().default(false),
});

type AddressFormValues = z.infer<typeof addressSchema>;

type FormMode = "none" | "add" | "edit";

export function CustomerProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout } = useAuthStore();
  const {
    data: locations,
    isPending: locationsPending,
    error: locationsError,
  } = useServiceLocations();
  const serviceLocations = useMemo(
    () => toServiceLocationOptions(locations),
    [locations],
  );

  const {
    data: addressesPage,
    isPending: addressesPending,
    error: addressesError,
    refetch: refetchAddresses,
  } = useAddresses(0, 50);
  const addresses = addressesPage?.content ?? [];
  const createAddress = useCreateAddress();
  const updateAddress = useUpdateAddress();
  const deleteAddress = useDeleteAddress();

  const [mode, setMode] = useState<FormMode>("none");
  const [editingAddress, setEditingAddress] = useState<AddressResponse | null>(null);
  const [accountSuccess, setAccountSuccess] = useState<string | null>(null);
  const [accountSubmitError, setAccountSubmitError] = useState<unknown>(null);
  const [addressSubmitError, setAddressSubmitError] = useState<unknown>(null);

  const isFirstAddress = addresses.length === 0;
  const defaultAddress = addresses.find((a) => a.defaultAddress);

  const onSaveAddress = async (values: AddressFormValues) => {
    setAddressSubmitError(null);
    try {
      const payload = {
        label: values.label,
        line1: values.line1,
        line2: values.line2 === "" ? null : values.line2,
        serviceLocationId: values.serviceLocationId,
        defaultAddress: isFirstAddress ? true : values.defaultAddress,
      };
      if (editingAddress) {
        await updateAddress.mutateAsync({ id: editingAddress.id, payload });
        setMode("none");
        setEditingAddress(null);
      } else {
        await createAddress.mutateAsync(payload);
        setMode("none");
      }
      await refetchAddresses();
    } catch (error) {
      setAddressSubmitError(error);
    }
  };

  const onEditAddress = (address: AddressResponse) => {
    setAddressSubmitError(null);
    setEditingAddress(address);
    setMode("edit");
  };

  const onAddAddress = () => {
    setAddressSubmitError(null);
    setEditingAddress(null);
    setMode("add");
  };

  const onCancelAddress = () => {
    setMode("none");
    setEditingAddress(null);
    setAddressSubmitError(null);
  };

  const onDeleteAddress = async (id: number) => {
    setAddressSubmitError(null);
    if (!window.confirm("Delete this saved address?")) return;
    try {
      await deleteAddress.mutateAsync(id);
      await refetchAddresses();
      if (mode === "edit" && editingAddress?.id === id) {
        setMode("none");
        setEditingAddress(null);
      }
    } catch (error) {
      setAddressSubmitError(error);
    }
  };

  /* ---------------------------------------------------------------- Account */
  const {
    register: registerAccount,
    handleSubmit: handleSubmitAccount,
    reset: resetAccount,
    setError: setAccountError,
    formState: { errors: accountErrors, isSubmitting: accountSubmitting },
  } = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema) as Resolver<AccountFormValues, unknown, AccountFormValues>,
    defaultValues: { fullName: "", phone: "" },
  });

  useEffect(() => {
    if (user) {
      resetAccount({ fullName: user.fullName, phone: user.phone ?? "" });
    }
  }, [user, resetAccount]);

  const onSaveAccount = async (values: AccountFormValues) => {
    setAccountSuccess(null);
    setAccountSubmitError(null);
    try {
      const updated = await updateCurrentUser({
        fullName: values.fullName,
        phone: values.phone === "" ? null : values.phone,
      });
      setAccountSuccess("Account details saved.");
      useAuthStore.setState({ user: updated });
    } catch (error) {
      const info = toApiError(error);
      setAccountSubmitError(error);
      if (info.validation) {
        Object.entries(info.validation).forEach(([field, message]) => {
          if (field === "fullName" || field === "phone") {
            setAccountError(field, { type: "server", message });
          }
        });
      }
    }
  };

  /* ---------------------------------------------------------------- Logout */
  const handleLogout = () => {
    clearAddressCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  if (addressesError || locationsError) {
    return <CustomerErrorState error={addressesError ?? locationsError} onRetry={refetchAddresses} />;
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title="Profile"
        description="Manage your account details and saved delivery addresses."
      />

      {/* -------------------------------- Account section -------------------------------- */}
      <Card>
        <div className="space-y-5">
          <PageHeading
            title="Account"
            description="Your name and phone can be edited here. Email is your account identity."
          />

          {accountSubmitError ? (
            <CustomerErrorState
              error={accountSubmitError}
              notFoundTitle="Could not save account details"
              notFoundDescription="Please check your details and try again."
            />
          ) : null}

          <form className="space-y-4" onSubmit={handleSubmitAccount(onSaveAccount)} noValidate>
            <FormErrorSummary
              message={
                accountSubmitError && !accountErrors.fullName && !accountErrors.phone
                  ? toApiError(accountSubmitError as Error).message ??
                    "Some fields need attention."
                  : null
              }
            />
            <SuccessMessage message={accountSuccess} />
            <TextField
              label="Full name"
              error={accountErrors.fullName?.message}
              registration={registerAccount("fullName")}
            />
            <TextField
              label="Phone number"
              hint="Optional. Include the country code, e.g. +91 98765 43210."
              error={accountErrors.phone?.message}
              registration={registerAccount("phone")}
            />
            <SubmitButton
              isSubmitting={accountSubmitting || createAddress.isPending}
              idleLabel="Save account details"
              busyLabel="Saving..."
            />
          </form>

          <dl className="space-y-3 pt-2 border-t border-slate-200">
            <ReadOnlyRow label="Email" value={user?.email ?? "—"} />
            <ReadOnlyRow label="Role" value={user?.role ?? "—"} />
            <ReadOnlyRow label="Customer id" value={user?.id?.toString() ?? "—"} />
            <ReadOnlyRow label="Member since" value={user?.createdAt ?? "—"} />
          </dl>

          <p className="text-sm text-slate-600">
            Want to change your password?{" "}
            <a
              href="/settings/password"
              className={`underline underline-offset-2 hover:text-bolder-blush ${PRESSABLE}`}
            >
              Change password
            </a>
          </p>
        </div>
      </Card>

      {/* ----------------------------- Addresses section ----------------------------- */}
      <Card>
        <div className="space-y-5">
          <div className="flex items-center justify-between">
            <PageHeading
              title="Saved addresses"
              description="Addresses you can deliver to. The default address is used at checkout."
            />
            <button
              type="button"
              onClick={onAddAddress}
              disabled={mode !== "none"}
              className={`inline-flex items-center rounded-md border border-transparent bg-bolder-rose px-4 py-2 text-sm font-medium text-bolder-bg hover:bg-bolder-rose-strong focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:opacity-60 ${PRESSABLE}`}
            >
              {mode === "add" ? "Adding…" : "Add address"}
            </button>
          </div>

          {addressSubmitError ? (
            <CustomerErrorState
              error={addressSubmitError}
              notFoundTitle="Could not save the address"
              notFoundDescription="Please check the details and try again."
            />
          ) : null}

          {addressesPending ? (
            <p className="text-sm text-slate-600">Loading your addresses…</p>
          ) : addresses.length === 0 ? (
            <p className="text-sm text-slate-600">
              You have no saved addresses yet. Add one to speed up checkout.
            </p>
          ) : (
            <>
              <ul role="list" className="divide-y divide-slate-200">
                {addresses.map((address) => (
                  <li
                    key={address.id}
                    className="p-4"
                    data-testid={`address-row-${address.id}`}
                  >
                    {mode === "edit" && editingAddress?.id === address.id ? (
                      <AddressEditForm
                        address={address}
                        serviceLocations={serviceLocations}
                        locationsPending={locationsPending}
                        onSave={onSaveAddress}
                        onCancel={onCancelAddress}
                        submitError={addressSubmitError}
                        isPending={updateAddress.isPending}
                        isFirstAddress={isFirstAddress}
                      />
                    ) : (
                      <AddressRow
                        address={address}
                        isDefault={address.id === defaultAddress?.id}
                        onEdit={() => onEditAddress(address)}
                        onDelete={() => onDeleteAddress(address.id)}
                      />
                    )}
                  </li>
                ))}
              </ul>

              {mode === "add" && (
                <AddressForm
                  serviceLocations={serviceLocations}
                  locationsPending={locationsPending}
                  onSave={onSaveAddress}
                  onCancel={onCancelAddress}
                  submitError={addressSubmitError}
                  isPending={createAddress.isPending}
                  isFirstAddress={isFirstAddress}
                />
              )}
            </>
          )}
        </div>
      </Card>

      {/* ----------------------------------- Logout ----------------------------------- */}
      <button
        type="button"
        onClick={handleLogout}
        className={`w-full inline-flex justify-center rounded-md border border-transparent bg-bolder-rose px-4 py-2.5 text-sm font-medium text-bolder-bg hover:bg-bolder-rose-strong focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 ${PRESSABLE}`}
      >
        Log out
      </button>
    </div>
  );
}

/* ---------------------------------------------------------------- helpers ------ */
const defaultAddressFormValues: AddressFormValues = {
  label: "",
  line1: "",
  line2: "",
  serviceLocationId: 0,
  defaultAddress: false,
};

function addressFormValuesFrom(address: AddressResponse): AddressFormValues {
  return {
    label: address.label,
    line1: address.line1,
    line2: address.line2 ?? "",
    serviceLocationId: address.serviceLocationId,
    defaultAddress: address.defaultAddress,
  };
}

/* ----------------------------------------------------------- AddressRow ------ */
interface AddressRowProps {
  address: AddressResponse;
  isDefault: boolean;
  onEdit: () => void;
  onDelete: () => void;
}

function AddressRow({ address, isDefault, onEdit, onDelete }: AddressRowProps) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
      <div>
        <p className="text-sm font-medium text-slate-900">{address.label}</p>
        <p className="text-xs text-slate-500">
          {address.line1}{address.line2 ? `, ${address.line2}` : ""} —{' '}
          {address.area}, {address.city} {address.pincode}
        </p>
      </div>
      <span
        className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-tighter ${
          isDefault
            ? "border-bolder-rose bg-bolder-rose/10 text-bolder-rose"
            : "border-slate-300 bg-slate-50 text-slate-600"
        }`}
      >
        {isDefault ? "Default" : "Saved"}
      </span>
      <div className="flex items-center gap-2 sm:flex-row">
        <button
          type="button"
          onClick={onEdit}
          className={`text-[12px] font-medium underline underline-offset-4 hover:text-bolder-blush ${PRESSABLE}`}
        >
          Edit
        </button>
        <button
          type="button"
          onClick={onDelete}
          className={`text-[12px] font-medium underline underline-offset-4 hover:text-red-600 ${PRESSABLE}`}
        >
          Delete
        </button>
      </div>
    </div>
  );
}

/* ----------------------------------------------------------- AddressForm ------- */
interface AddressFormProps {
  serviceLocations: { id: string; label: string }[];
  locationsPending: boolean;
  onSave: (values: AddressFormValues) => void;
  onCancel: () => void;
  submitError: unknown;
  isPending: boolean;
  isFirstAddress: boolean;
}

function AddressForm({
  serviceLocations,
  locationsPending,
  onSave,
  onCancel,
  submitError,
  isPending,
  isFirstAddress,
}: AddressFormProps) {
  const selectId = useId();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema) as Resolver<AddressFormValues, unknown, AddressFormValues>,
    defaultValues: {
      ...defaultAddressFormValues,
      defaultAddress: isFirstAddress,
    },
  });

  const onSubmit = (values: AddressFormValues) => {
    onSave(values);
  };

  return (
    <div className="space-y-5">
      <PageHeading
        title={isFirstAddress ? "Add your first address" : "Add address"}
        description="Saved addresses speed up checkout."
      />
      {submitError ? (
        <CustomerErrorState
          error={submitError}
          notFoundTitle="Could not add the address"
          notFoundDescription="Please check the details and try again."
        />
      ) : null}
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormErrorSummary
          message={
            submitError && !errors.label && !errors.line1 && !errors.serviceLocationId
              ? toApiError(submitError as Error).message ??
                "Some fields need attention."
              : null
          }
        />
        <TextField
          label="Label"
          hint="e.g. Home or Office"
          error={errors.label?.message}
          registration={register("label")}
        />
        <TextField
          label="Address line 1"
          error={errors.line1?.message}
          registration={register("line1")}
        />
        <TextField
          label="Address line 2"
          hint="Optional. Apartment, suite, floor, etc."
          error={errors.line2?.message}
          registration={register("line2")}
        />
        {locationsPending ? (
          <p className="text-sm text-slate-600">Loading service areas…</p>
        ) : (
          <div>
            <label htmlFor={selectId} className="block text-sm font-medium text-slate-700">
              Service area
            </label>
            <select
              id={selectId}
              {...register("serviceLocationId")}
              className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm ${
                errors.serviceLocationId ? "border-red-500" : ""
              }`}
            >
              <option value="">Select a service area</option>
              {serviceLocations.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
            {errors.serviceLocationId && (
              <p className="mt-1 text-xs text-red-600">{errors.serviceLocationId.message}</p>
            )}
          </div>
        )}
        <CheckboxField
          label="Set as default address"
          hint={
            isFirstAddress
              ? "This is your first address, so it becomes the default automatically."
              : "Checked addresses are used first at checkout."
          }
          registration={register("defaultAddress")}
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isPending}
            className={`inline-flex items-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60 ${PRESSABLE}`}
          >
            Cancel
          </button>
          <SubmitButton
            isSubmitting={isPending}
            idleLabel="Save address"
            busyLabel="Saving…"
          />
        </div>
      </form>
    </div>
  );
}

/* -------------------------------------------------------- AddressEditForm ------ */
interface AddressEditFormProps {
  address: AddressResponse;
  serviceLocations: { id: string; label: string }[];
  locationsPending: boolean;
  onSave: (values: AddressFormValues) => void;
  onCancel: () => void;
  submitError: unknown;
  isPending: boolean;
  isFirstAddress: boolean;
}

function AddressEditForm({
  address,
  serviceLocations,
  locationsPending,
  onSave,
  onCancel,
  submitError,
  isPending,
  isFirstAddress,
}: AddressEditFormProps) {
  const selectId = useId();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema) as Resolver<AddressFormValues, unknown, AddressFormValues>,
    defaultValues: addressFormValuesFrom(address),
  });

  const onSubmit = (values: AddressFormValues) => {
    onSave(values);
  };

  useEffect(() => {
    reset(addressFormValuesFrom(address));
  }, [address, reset]);

  return (
    <div className="space-y-5">
      <PageHeading title="Edit address" description="Changes are saved to your address book." />
      {submitError ? (
        <CustomerErrorState
          error={submitError}
          notFoundTitle="Could not update the address"
          notFoundDescription="Please check the details and try again."
        />
      ) : null}
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormErrorSummary
          message={
            submitError && !errors.label && !errors.line1 && !errors.serviceLocationId
              ? toApiError(submitError as Error).message ??
                "Some fields need attention."
              : null
          }
        />
        <TextField
          label="Label"
          hint="e.g. Home or Office"
          error={errors.label?.message}
          registration={register("label")}
        />
        <TextField
          label="Address line 1"
          error={errors.line1?.message}
          registration={register("line1")}
        />
        <TextField
          label="Address line 2"
          hint="Optional. Apartment, suite, floor, etc."
          error={errors.line2?.message}
          registration={register("line2")}
        />
        {locationsPending ? (
          <p className="text-sm text-slate-600">Loading service areas…</p>
        ) : (
          <div>
            <label htmlFor={selectId} className="block text-sm font-medium text-slate-700">
              Service area
            </label>
            <select
              id={selectId}
              {...register("serviceLocationId")}
              className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm ${
                errors.serviceLocationId ? "border-red-500" : ""
              }`}
            >
              <option value="">Select a service area</option>
              {serviceLocations.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
            {errors.serviceLocationId && (
              <p className="mt-1 text-xs text-red-600">{errors.serviceLocationId.message}</p>
            )}
          </div>
        )}
        <CheckboxField
          label="Set as default address"
          hint={
            isFirstAddress
              ? "This is your only address, so it stays the default."
              : "Checking this makes it the default and clears the previous one."
          }
          registration={register("defaultAddress")}
        />
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isPending || isSubmitting}
            className={`inline-flex items-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60 ${PRESSABLE}`}
          >
            Cancel
          </button>
          <SubmitButton
            isSubmitting={isPending || isSubmitting}
            idleLabel="Save changes"
            busyLabel="Saving…"
          />
        </div>
      </form>
    </div>
  );
}
