import { Link, useNavigate, useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { FadeIn } from "@/motion/FadeIn";
import { FieldMessage } from "@/motion/FieldMessage";
import { SlideUp } from "@/motion/SlideUp";
import { FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

const loginSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type LoginFormData = z.infer<typeof loginSchema>;

/** The two inputs share one control treatment; only the type and id differ. */
const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isLoading, error } = useAuthStore();
  // `registeredEmail` is set by the vendor registration page on success, so a florist
  // who just applied only types their password. `from` is already used by
  // `ProtectedRoute` to remember where an unauthenticated visitor was heading.
  const locationState = location.state as { from?: string; registeredEmail?: string } | null;
  const from = locationState?.from || "/";
  const registeredEmail = locationState?.registeredEmail ?? "";

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: registeredEmail, password: "" },
  });

  const onSubmit = async (data: LoginFormData) => {
    await login(data.email, data.password);
    const { isAuthenticated } = useAuthStore.getState();
    if (isAuthenticated) {
      navigate(from, { replace: true });
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
      <SlideUp className="w-full max-w-md space-y-8">
        <div>
          <h2 className="mt-6 text-3xl font-bold text-brand-700">Welcome back</h2>
          <p className="mt-2 text-sm text-slate-600">Sign in to your FlowerConnect account</p>
        </div>

        {error && (
          <FadeIn role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">
            {error}
          </FadeIn>
        )}

        {registeredEmail && (
          <FadeIn
            role="status"
            className="rounded-md bg-brand-50 p-4 text-sm text-brand-900"
          >
            Your florist application is in. Sign in with the email you registered and we will
            take you to your vendor dashboard.
          </FadeIn>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
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
                className={`${CONTROL_CLASS} ${errors.email ? "border-red-500" : ""}`}
                {...register("email")}
              />
              <FieldMessage id="email-note" message={errors.email?.message} reserve />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                aria-invalid={errors.password ? true : undefined}
                aria-describedby={errors.password ? "password-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.password ? "border-red-500" : ""}`}
                {...register("password")}
              />
              <FieldMessage id="password-note" message={errors.password?.message} reserve />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className={`w-full rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
          >
            {isLoading ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <p className="text-center text-sm text-slate-600">
          New to FlowerConnect?{" "}
          <Link
            to="/register"
            className="font-medium text-brand-600 transition-colors duration-micro ease-standard hover:text-brand-700"
          >
            Create an account
          </Link>
        </p>

        <p className="text-center text-sm text-slate-600">
          Own a flower shop?{" "}
          <Link
            to="/vendor/register"
            className="font-medium text-brand-600 transition-colors duration-micro ease-standard hover:text-brand-700"
          >
            Register it on FlowerConnect
          </Link>
        </p>
      </SlideUp>
    </div>
  );
}
