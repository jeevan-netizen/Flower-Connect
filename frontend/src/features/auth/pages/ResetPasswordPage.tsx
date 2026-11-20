import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { SlideUp } from "@/motion/SlideUp";
import { FadeIn } from "@/motion/FadeIn";
import { PasswordField } from "@/features/auth/components/PasswordField";
import { resetPassword } from "@/features/auth/api";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const resetPasswordSchema = z
  .object({
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

type ResetPasswordFormData = z.infer<typeof resetPasswordSchema>;

const SUBMIT_CLASS = `rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE} ${FOCUS_RING}`;

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const navigate = useNavigate();

  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  const onSubmit = async (data: ResetPasswordFormData) => {
    setSubmitError(null);
    setSuccess(null);

    try {
      await resetPassword({ token, newPassword: data.newPassword });
      setSuccess(
        "Your password has been reset. You can now sign in with your new password.",
      );
    } catch (error) {
      const info = toApiError(error);
      setSubmitError(info);
      if (info.validation) {
        Object.entries(info.validation).forEach(([field, message]) => {
          setError(field as keyof ResetPasswordFormData, {
            type: "server",
            message,
          });
        });
      }
    }
  };

  if (!token) {
    return (
      <SlideUp className="mx-auto max-w-md space-y-6">
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          Invalid reset link. Please request a new one.
        </div>
        <button
          type="button"
          onClick={() => navigate("/forgot-password")}
          className={`${SUBMIT_CLASS}`}
        >
          Request a new reset link
        </button>
      </SlideUp>
    );
  }

  if (success) {
    return (
      <SlideUp className="mx-auto max-w-md space-y-6">
        <div className="rounded-md bg-brand-50 p-4 text-sm text-brand-900">
          {success}
        </div>
        <button
          type="button"
          onClick={() => navigate("/login")}
          className={`${SUBMIT_CLASS}`}
        >
          Sign in
        </button>
      </SlideUp>
    );
  }

  return (
    <SlideUp className="mx-auto max-w-md space-y-6">
      <div>
        <h2 className="mt-6 text-3xl font-bold text-brand-700">Reset password</h2>
        <p className="mt-2 text-sm text-slate-600">
          Choose a new password.
        </p>
      </div>

      {submitError && (
        <FadeIn role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {submitError.message}
        </FadeIn>
      )}

      <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="space-y-4">
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
          disabled={isSubmitting}
          className={`w-full ${SUBMIT_CLASS}`}
        >
          {isSubmitting ? "Resetting..." : "Reset password"}
        </button>
      </form>

      <p className="text-center text-sm text-slate-600">
        Need a new link?{" "}
        <Link
          to="/forgot-password"
          className="font-medium text-brand-600 transition-colors duration-micro ease-standard hover:text-brand-700"
        >
          Request one
        </Link>
      </p>
    </SlideUp>
  );
}
