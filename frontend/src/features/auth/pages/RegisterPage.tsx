import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { FadeIn } from "@/motion/FadeIn";
import { FieldMessage } from "@/motion/FieldMessage";
import { SlideUp } from "@/motion/SlideUp";
import { FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

const registerSchema = z
  .object({
    fullName: z.string().min(1, "Full name is required").max(128, "Full name must not exceed 128 characters"),
    email: z.string().min(1, "Email is required").email("Enter a valid email address").max(255, "Email must not exceed 255 characters"),
    phone: z
      .string()
      .optional()
      .refine(
        (val) => !val || /^\+?[0-9]{7,15}$/.test(val),
        "Enter a valid phone number (7-15 digits, optional + prefix)",
      ),
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(128, "Password must not exceed 128 characters"),
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

/** The five inputs share one control treatment; only type, id and label differ. */
const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;

export function RegisterPage() {
  const navigate = useNavigate();
  const { register: registerUser, isLoading, error } = useAuthStore();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (data: RegisterFormData) => {
    await registerUser(data.fullName, data.email, data.phone || null, data.password);
    const { isAuthenticated } = useAuthStore.getState();
    if (isAuthenticated) {
      navigate("/");
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
      <SlideUp className="w-full max-w-md space-y-8">
        <div>
          <h2 className="mt-6 text-3xl font-bold text-brand-700">Create your account</h2>
          <p className="mt-2 text-sm text-slate-600">Join FlowerConnect today</p>
        </div>

        {error && (
          <FadeIn role="alert" className="rounded-md bg-red-50 p-4 text-sm text-red-700">
            {error}
          </FadeIn>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
            <div>
              <label htmlFor="fullName" className="block text-sm font-medium text-slate-700">
                Full name
              </label>
              <input
                id="fullName"
                type="text"
                autoComplete="name"
                required
                aria-invalid={errors.fullName ? true : undefined}
                aria-describedby={errors.fullName ? "fullName-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.fullName ? "border-red-500" : ""}`}
                {...register("fullName")}
              />
              <FieldMessage id="fullName-note" message={errors.fullName?.message} reserve />
            </div>

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
              <label htmlFor="phone" className="block text-sm font-medium text-slate-700">
                Phone number
              </label>
              <input
                id="phone"
                type="tel"
                autoComplete="tel"
                placeholder="+1234567890"
                aria-invalid={errors.phone ? true : undefined}
                aria-describedby={errors.phone ? "phone-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.phone ? "border-red-500" : ""}`}
                {...register("phone")}
              />
              <FieldMessage id="phone-note" message={errors.phone?.message} reserve />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                aria-invalid={errors.password ? true : undefined}
                aria-describedby={errors.password ? "password-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.password ? "border-red-500" : ""}`}
                {...register("password")}
              />
              <FieldMessage id="password-note" message={errors.password?.message} reserve />
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700">
                Confirm password
              </label>
              <input
                id="confirmPassword"
                type="password"
                autoComplete="new-password"
                required
                aria-invalid={errors.confirmPassword ? true : undefined}
                aria-describedby={errors.confirmPassword ? "confirmPassword-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.confirmPassword ? "border-red-500" : ""}`}
                {...register("confirmPassword")}
              />
              <FieldMessage
                id="confirmPassword-note"
                message={errors.confirmPassword?.message}
                reserve
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className={`w-full rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
          >
            {isLoading ? "Creating account..." : "Create account"}
          </button>
        </form>

        <p className="text-center text-sm text-slate-600">
          Already have an account?{" "}
          <Link
            to="/login"
            className="font-medium text-brand-600 transition-colors duration-micro ease-standard hover:text-brand-700"
          >
            Sign in
          </Link>
        </p>
      </SlideUp>
    </div>
  );
}
