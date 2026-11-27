import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { SlideUp } from "@/motion/SlideUp";
import { FadeIn } from "@/motion/FadeIn";
import { PasswordField } from "@/features/auth/components/PasswordField";
import { changePassword } from "@/features/auth/api";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: z
      .string()
      .min(8, "New password must be at least 8 characters")
      .max(128, "New password must not exceed 128 characters"),
    confirmPassword: z.string().min(1, "Please confirm your new password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "New passwords do not match",
    path: ["confirmPassword"],
  });

type ChangePasswordFormData = z.infer<typeof changePasswordSchema>;

const SUBMIT_CLASS = `rounded-md border border-transparent bg-bolder-rose-action py-2.5 px-4 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE} ${FOCUS_RING}`;

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { isLoading } = useAuthStore();

  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
    setError,
  } = useForm<ChangePasswordFormData>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const onSubmit = async (data: ChangePasswordFormData) => {
    setSubmitError(null);
    setSuccess(null);

    try {
      await changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      setSuccess(
        "Password updated. Your other active sessions have been logged out; sign in again with your new password on any device.",
      );
      reset({ currentPassword: "", newPassword: "", confirmPassword: "" });
    } catch (error) {
      const info = toApiError(error);
      setSubmitError(info);
      if (info.validation) {
        Object.entries(info.validation).forEach(([field, message]) => {
          setError(field as keyof ChangePasswordFormData, {
            type: "server",
            message,
          });
        });
      }
    }
  };

  if (success) {
    return (
      <SlideUp className="mx-auto max-w-md space-y-6">
        <div className="rounded-md bg-bolder-blush-light p-4 text-sm text-bolder-rose-deep">
          {success}
        </div>
        <p className="text-sm text-bolder-muted">
          Your current access token stays valid until it expires, but it cannot
          refresh — sign in again on any other device with your new password.
        </p>
        <button
          type="button"
          onClick={() => navigate("/")}
          className={`${SUBMIT_CLASS}`}
        >
          Back to home
        </button>
      </SlideUp>
    );
  }

  return (
    <SlideUp className="mx-auto max-w-md space-y-6">
      <div>
        <h2 className="mt-6 text-3xl font-bold text-bolder-rose-strong">Change password</h2>
        <p className="mt-2 text-sm text-bolder-muted">
          Update your account password.
        </p>
      </div>

      {submitError && (
        <FadeIn role="alert" className="rounded-md bg-bolder-danger-surface p-4 text-sm text-bolder-danger">
          {submitError.message}
        </FadeIn>
      )}

      <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="space-y-4">
          <PasswordField
            label="Current password"
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            registration={register("currentPassword")}
          />
          <PasswordField
            label="New password"
            autoComplete="new-password"
            error={errors.newPassword?.message}
            registration={register("newPassword")}
          />
          <PasswordField
            label="Confirm new password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            registration={register("confirmPassword")}
          />
        </div>

        <button
          type="submit"
          disabled={isSubmitting || isLoading}
          className={`w-full ${SUBMIT_CLASS}`}
        >
          {isSubmitting || isLoading ? "Changing..." : "Change password"}
        </button>
      </form>

      <p className="text-center text-sm text-bolder-muted">
        Back to{" "}
        <Link
          to="/"
          className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
        >
          home
        </Link>
      </p>
    </SlideUp>
  );
}
