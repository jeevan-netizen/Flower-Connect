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

const SUBMIT_CLASS = `rounded-md border border-transparent bg-bolder-rose-action py-2.5 px-4 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE} ${FOCUS_RING}`;

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
        <div className="rounded-md bg-bolder-blush-light p-4 text-sm text-bolder-rose-deep">
          {success}
        </div>
        <p className="text-sm text-bolder-muted">
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
        <h2 className="mt-6 text-3xl font-bold text-bolder-rose-strong">Forgot password</h2>
        <p className="mt-2 text-sm text-bolder-muted">
          Enter your email and we will send you a reset link.
        </p>
      </div>

      {submitError && (
        <FadeIn role="alert" className="rounded-md bg-bolder-danger-surface p-4 text-sm text-bolder-danger">
          {submitError.message}
        </FadeIn>
      )}

      <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-bolder-text">
            Email address
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? "email-note" : undefined}
            className={`mt-1 block w-full rounded-md border-bolder-line-control shadow-sm sm:text-sm focus:border-bolder-rose focus:ring-bolder-rose ${errors.email ? "border-bolder-danger-border" : ""}`}
            {...register("email")}
          />
          <p id="email-note" className={`mt-1 text-xs text-bolder-danger ${errors.email ? "" : "hidden"}`}>
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

      <p className="text-center text-sm text-bolder-muted">
        Remember your password?{" "}
        <Link
          to="/login"
          className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
        >
          Sign in
        </Link>
      </p>
    </SlideUp>
  );
}
