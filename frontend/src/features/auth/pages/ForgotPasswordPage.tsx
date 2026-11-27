import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { SlideUp } from "@/motion/SlideUp";
import { FadeIn } from "@/motion/FadeIn";
import { forgotPassword } from "@/features/auth/api";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const forgotPasswordSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email address"),
});

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;

const SUBMIT_CLASS = `rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE} ${FOCUS_RING}`;

export function ForgotPasswordPage() {
  const navigate = useNavigate();

  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const onSubmit = async (data: ForgotPasswordFormData) => {
    setSubmitError(null);
    setSuccess(null);

    try {
      await forgotPassword({ email: data.email.trim().toLowerCase() });
      setSuccess(
        "If an account exists for that email, a password-reset link has been sent. Check your inbox.",
      );
    } catch (error) {
      const info = toApiError(error);
      setSubmitError(info);
    }
  };

  if (success) {
    return (
      <SlideUp className="mx-auto max-w-md space-y-6">
        <div className="rounded-md bg-brand-50 p-4 text-sm text-brand-900">
          {success}
        </div>
        <p className="text-sm text-slate-600">
          The reset link is single-use and expires after 30 minutes.
        </p>
        <button
          type="button"
          onClick={() => navigate("/login")}
          className={`${SUBMIT_CLASS}`}
        >
          Back to sign in
        </button>
      </SlideUp>
    );
  }

  return (
    <SlideUp className="mx-auto max-w-md space-y-6">
      <div>
        <h2 className="mt-6 text-3xl font-bold text-brand-700">Forgot password</h2>
        <p className="mt-2 text-sm text-slate-600">
          Enter your email and we will send you a reset link.
        </p>
      </div>

      {submitError && (
        <FadeIn role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {submitError.message}
        </FadeIn>
      )}

      <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email address
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? "email-note" : undefined}
            className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm focus:border-brand-500 focus:ring-brand-500 ${errors.email ? "border-red-500" : ""}`}
            {...register("email")}
          />
          <p id="email-note" className={`mt-1 text-xs text-red-600 ${errors.email ? "" : "hidden"}`}>
            {errors.email?.message}
          </p>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className={`w-full ${SUBMIT_CLASS}`}
        >
          {isSubmitting ? "Sending..." : "Send reset link"}
        </button>
      </form>

      <p className="text-center text-sm text-slate-600">
        Remember your password?{" "}
        <Link
          to="/login"
          className="font-medium text-brand-600 transition-colors duration-micro ease-standard hover:text-brand-700"
        >
          Sign in
        </Link>
      </p>
    </SlideUp>
  );
}
