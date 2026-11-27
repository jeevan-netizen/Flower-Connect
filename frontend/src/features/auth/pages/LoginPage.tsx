import { Link, useNavigate, useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { PasswordField } from "@/features/auth/components/PasswordField";
import { FadeIn } from "@/motion/FadeIn";
import { FieldMessage } from "@/motion/FieldMessage";
import { SlideUp } from "@/motion/SlideUp";
import { FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

const loginSchema = z.object({
  email: z.string().min(1, "Email is required").email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type LoginFormData = z.infer<typeof loginSchema>;

/** Seeded role name for farmers (V35 migration). */
const FARMER_ROLE = "FARMER";

/** The two inputs share one control treatment; only the type and id differ. */
const CONTROL_CLASS = `mt-1 block w-full rounded-md border-bolder-line-control shadow-sm ${FIELD_TRANSITION} focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm`;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isLoading, error, setError, setJustLoggedIn, logout } = useAuthStore();
  // `registeredEmail` is set by the vendor registration page on success, so a florist
  // who just applied only types their password. `from` is already used by
  // `ProtectedRoute` to remember where an unauthenticated visitor was heading.
  // `farmerEntry` marks the farmer entry point from the front page, so a successful
  // farmer login is sent to the farmer portal and a non-farmer is refused clearly.
  const locationState = location.state as {
    from?: string;
    farmerEntry?: boolean;
    registeredEmail?: string;
  } | null;
  const from = locationState?.from || "/";
  const farmerEntry = locationState?.farmerEntry === true;
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
    const { isAuthenticated, user } = useAuthStore.getState();
    if (isAuthenticated) {
      if (farmerEntry && user?.role !== FARMER_ROLE) {
        // Refuse the farmer entry point for non-farmer accounts without exposing
        // any farmer data; the general login remains available.
        const refusalMessage =
          `Farmer sign-in is for farmer accounts. Your account is registered as ${user?.role}. Use the general login instead.`;
        // End the non-farmer session so the normal unauthenticated guard remains
        // active, then show the explanation on the login page.
        logout();
        setError(refusalMessage);
        // Keep the explanation visible, but switch this page to the ordinary
        // login flow so the user can retry without the farmer-entry restriction.
        navigate("/login", {
          replace: true,
          state: { from: "/", farmerEntry: false, registeredEmail },
        });
        return;
      }
      navigate(from, { replace: true });
      // Defer the reset past this commit so RequireUnauth (mounted above this
      // page while LoginPage unmounts) does not race with the navigation. Once
      // LoginPage has unmounted, nothing reacts to the flag and a later
      // revisit to /login by an authenticated user is redirected away.
      setTimeout(() => setJustLoggedIn(false), 0);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bolder-bg py-12 px-4 sm:px-6 lg:px-8">
      <SlideUp className="w-full max-w-md space-y-8">
        <div>
          <h2 className="mt-6 text-3xl font-bold text-bolder-rose-strong">Welcome back</h2>
          <p className="mt-2 text-sm text-bolder-muted">Sign in to your FlowerConnect account</p>
        </div>

        {error && (
          <FadeIn role="alert" className="rounded-md bg-bolder-danger-surface p-4 text-sm text-bolder-danger">
            {error}
          </FadeIn>
        )}

        {registeredEmail && (
          <FadeIn
            role="status"
            className="rounded-md bg-bolder-blush-light p-4 text-sm text-bolder-rose-deep"
          >
            Your florist application is in. Sign in with the email you registered and we will
            take you to your vendor dashboard.
          </FadeIn>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
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
                className={`${CONTROL_CLASS} ${errors.email ? "border-bolder-danger-border" : ""}`}
                {...register("email")}
              />
              <FieldMessage id="email-note" message={errors.email?.message} reserve />
            </div>

            <div>
              <PasswordField
                label="Password"
                autoComplete="current-password"
                error={errors.password?.message}
                registration={register("password")}
              />
              <p className="mt-1 text-right text-sm">
                <Link
                  to="/forgot-password"
                  className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
                >
                  Forgot password?
                </Link>
              </p>
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className={`w-full rounded-md border border-transparent bg-bolder-rose-action py-2.5 px-4 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
          >
            {isLoading ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <p className="text-center text-sm text-bolder-muted">
          New to FlowerConnect?{" "}
          <Link
            to="/register"
            className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
          >
            Create an account
          </Link>
        </p>

        <p className="text-center text-sm text-bolder-muted">
          Growing flowers?{" "}
          <Link
            to="/farmer/register"
            className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
          >
            Register as a Farmer
          </Link>
        </p>

        <p className="text-center text-sm text-bolder-muted">
          Own a flower shop?{" "}
          <Link
            to="/vendor/register"
            className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
          >
            Register it on FlowerConnect
          </Link>
        </p>
      </SlideUp>
    </div>
  );
}
