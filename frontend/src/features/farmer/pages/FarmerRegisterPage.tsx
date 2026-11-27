import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { PasswordField } from "@/features/auth/components/PasswordField";
import { FadeIn } from "@/motion/FadeIn";
import { FieldMessage } from "@/motion/FieldMessage";
import { SlideUp } from "@/motion/SlideUp";
import { FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

const farmerRegisterSchema = z
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
    address: z.string().max(255, "Address must not exceed 255 characters").optional(),
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

type FarmerRegisterFormData = z.infer<typeof farmerRegisterSchema>;

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-bolder-line-control shadow-sm ${FIELD_TRANSITION} focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm`;

export function FarmerRegisterPage() {
  const navigate = useNavigate();
  const { registerFarmer, isLoading, error } = useAuthStore();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FarmerRegisterFormData>({
    resolver: zodResolver(farmerRegisterSchema),
  });

  const onSubmit = async (data: FarmerRegisterFormData) => {
    try {
      await registerFarmer(data.fullName, data.email, data.phone || null, data.password, data.address || undefined);
      navigate("/login", { state: { registeredEmail: data.email, from: "/farmer" }, replace: true });
    } catch {
      // Error is handled by the auth store
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bolder-bg py-12 px-4 sm:px-6 lg:px-8">
      <SlideUp className="w-full max-w-md space-y-8">
        <div>
          <h2 className="mt-6 text-3xl font-bold text-bolder-rose-strong">Register as a Farmer</h2>
          <p className="mt-2 text-sm text-bolder-muted">Create your farmer account to connect with local florists</p>
        </div>

        {error && (
          <FadeIn role="alert" className="rounded-md bg-bolder-danger-surface p-4 text-sm text-bolder-danger">
            {error}
          </FadeIn>
        )}

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
            <div>
                <label htmlFor="fullName" className="block text-sm font-medium text-bolder-text">
                Full name
              </label>
              <input
                id="fullName"
                type="text"
                autoComplete="name"
                required
                aria-invalid={errors.fullName ? true : undefined}
                aria-describedby={errors.fullName ? "fullName-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.fullName ? "border-bolder-danger-border" : ""}`}
                {...register("fullName")}
              />
              <FieldMessage id="fullName-note" message={errors.fullName?.message} reserve />
            </div>

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
                <label htmlFor="phone" className="block text-sm font-medium text-bolder-text">
                Phone number
              </label>
              <input
                id="phone"
                type="tel"
                autoComplete="tel"
                placeholder="+1234567890"
                aria-invalid={errors.phone ? true : undefined}
                aria-describedby={errors.phone ? "phone-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.phone ? "border-bolder-danger-border" : ""}`}
                {...register("phone")}
              />
              <FieldMessage id="phone-note" message={errors.phone?.message} reserve />
            </div>

            <div>
                <label htmlFor="address" className="block text-sm font-medium text-bolder-text">
                Address
              </label>
              <input
                id="address"
                type="text"
                autoComplete="address-line1"
                placeholder="Your farm or residence address"
                aria-invalid={errors.address ? true : undefined}
                aria-describedby={errors.address ? "address-note" : undefined}
                className={`${CONTROL_CLASS} ${errors.address ? "border-bolder-danger-border" : ""}`}
                {...register("address")}
              />
              <FieldMessage id="address-note" message={errors.address?.message} reserve />
            </div>

            <div>
              <PasswordField
                label="Password"
                autoComplete="new-password"
                error={errors.password?.message}
                registration={register("password")}
              />
            </div>

            <div>
              <PasswordField
                label="Confirm password"
                autoComplete="new-password"
                error={errors.confirmPassword?.message}
                registration={register("confirmPassword")}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className={`w-full rounded-md border border-transparent bg-bolder-rose-action py-3 px-4 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
          >
            {isLoading ? "Creating account..." : "Create account"}
          </button>
        </form>

        <p className="text-center text-sm text-bolder-muted">
          Already have an account?{" "}
          <Link
            to="/login"
            className="font-medium text-bolder-rose-action transition-colors duration-micro ease-standard hover:text-bolder-rose-strong"
          >
            Sign in
          </Link>
        </p>
      </SlideUp>
    </div>
  );
}
