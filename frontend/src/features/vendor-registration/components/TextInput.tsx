import { useId } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { FieldMessage } from "@/motion/FieldMessage";
import { FIELD_TRANSITION } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;
const ERROR_BORDER = "border-red-500";

type InputType = "text" | "email" | "tel" | "password";

interface TextInputProps {
  label: string;
  type?: InputType;
  error?: string;
  hint?: string;
  multiline?: boolean;
  rows?: number;
  autoComplete?: string;
  registration: UseFormRegisterReturn;
}

/**
 * A type-aware text field for the registration form.
 *
 * The vendor area's `TextField` is deliberately input-type agnostic for profile
 * editing, where every value really is free text. Registration is not: it carries
 * an email address and two password fields, and a `type="text"` password field
 * would render the secret in clear text. That is why this component exists instead
 * of widening Task 2.9's shared field.
 *
 * It owns markup and accessible wiring only (`htmlFor`, `aria-invalid`,
 * `aria-describedby`); value handling stays with react-hook-form and zod.
 */
export function TextInput({
  label,
  type = "text",
  error,
  hint,
  multiline,
  rows = 4,
  autoComplete,
  registration,
}: TextInputProps) {
  const id = useId();
  const describedBy = error || hint ? id : undefined;
  const props = registration;

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {multiline ? (
        <textarea
          id={id}
          rows={rows}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""}`}
          {...props}
        />
      ) : (
        <input
          id={id}
          type={type}
          autoComplete={autoComplete}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""}`}
          {...props}
        />
      )}
      <FieldMessage id={id} hint={hint} message={error} reserve />
    </div>
  );
}