import { useId, useState } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { FieldMessage } from "@/motion/FieldMessage";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;
const ERROR_BORDER = "border-red-500";

interface PasswordFieldProps {
  label: string;
  error?: string;
  hint?: string;
  autoComplete?: string;
  registration: UseFormRegisterReturn;
}

/**
 * A password field with an accessible show/hide toggle.
 *
 * The field is masked by default. Toggling visibility flips only this field's
 * `type` between `password` and `text`; it never reads or transmits a stored
 * password. The toggle is a plain `type="button"` so it cannot submit its form.
 *
 * It owns markup and accessible wiring only; value handling stays with
 * react-hook-form and zod.
 */
export function PasswordField({
  label,
  error,
  hint,
  autoComplete,
  registration,
}: PasswordFieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const describedBy = error || hint ? id : undefined;
  const props = registration;

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={visible ? "text" : "password"}
          autoComplete={autoComplete}
          required
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""} pr-10`}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-pressed={visible}
          aria-label={visible ? "Hide password" : "Show password"}
          className={`absolute inset-y-0 right-0 pr-3 flex items-center ${PRESSABLE} ${FOCUS_RING} text-slate-500 hover:text-slate-700`}
        >
          <span className="sr-only">{visible ? "Hide password" : "Show password"}</span>
          {visible ? (
            <svg
              className="h-4 w-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
              focusable="false"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7h9.043c-.446 1.418-1.437 2.5 2.583 2.5zM7.5 6.693c0-1.614 1.195-2.943 2.72-3.27 1.446-.316 3.174.379 4.578 1.427a9.06 9.06 0 011.272 2.525c.586-1.298 1.532-2.379 2.72-2.379 1.168 0 2.128.924 2.571 2.152a9.036 9.036 0 012.332 2.867c.866 1.055 1.325 2.347 1.325 3.764 0 2.438-1.017 4.65-2.583 5.886h9.043c1.274-4.049-5.064-7-9.543-7-2.26 0-4.395.833-5.943 2.203z"
              />
            </svg>
          ) : (
            <svg
              className="h-4 w-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
              focusable="false"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.543 7h-9.043c-.446-1.418-1.437-2.5-2.583-2.5zM7.5 17.306c0-1.418 1.195-2.583 2.72-2.867 1.446-.316 3.174.379 4.578 1.427a9.06 9.06 0 011.272 2.526c-.586 1.298-1.532 2.379-2.72 2.379-1.168 0-2.128-.924-2.571-2.152a9.036 9.036 0 01-2.332-2.867c-.866-1.055-1.325-2.347-1.325-3.764 0-2.438 1.017-4.65 2.583-5.885h-9.043c-1.274 4.049 5.064 7 9.543 7 2.26 0 4.395-.833 5.943-2.203z"
              />
            </svg>
          )}
        </button>
      </div>
      <FieldMessage id={id} hint={hint} message={error} reserve />
    </div>
  );
}
