import { useId } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { FadeIn } from "@/motion/FadeIn";
import { FieldMessage } from "@/motion/FieldMessage";
import { FlowerSuccess } from "@/motion/FlowerSuccess";
import { FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";

/*
 * Durations and easings come from the motion tokens via `tailwind.config.ts`
 * (`duration-micro ease-standard`), never from a literal millisecond value.
 * `PRESSABLE` is the app-wide press feedback and lives in `@/motion/pressable` so
 * every feature composes one definition; the focus ring stays out of it, because
 * press feedback must not cost a keyboard user the visible indicator.
 */
const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;
const ERROR_BORDER = "border-red-500";

interface TextFieldProps {
  label: string;
  error?: string;
  hint?: string;
  multiline?: boolean;
  rows?: number;
  registration: UseFormRegisterReturn;
}

/**
 * Thin wrapper over react-hook-form's `register` output. It only owns the
 * markup and the accessible wiring (`htmlFor`, `aria-invalid`, `aria-describedby`);
 * value handling stays with react-hook-form and zod.
 */
export function TextField({
  label,
  error,
  hint,
  multiline,
  rows = 4,
  registration,
}: TextFieldProps) {
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
          type="text"
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

interface NumberFieldProps {
  label: string;
  error?: string;
  hint?: string;
  step?: string;
  min?: string;
  registration: UseFormRegisterReturn;
}

export function NumberField({ label, error, hint, step = "0.01", min = "0", registration }: NumberFieldProps) {
  const id = useId();
  const describedBy = error || hint ? id : undefined;
  const props = registration;

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        type="number"
        inputMode="decimal"
        min={min}
        step={step}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`${CONTROL_CLASS} ${error ? ERROR_BORDER : ""}`}
        {...props}
      />
      <FieldMessage id={id} hint={hint} message={error} reserve />
    </div>
  );
}

interface CheckboxFieldProps {
  label: string;
  hint?: string;
  registration: UseFormRegisterReturn;
}

export function CheckboxField({ label, hint, registration }: CheckboxFieldProps) {
  const id = useId();
  const props = registration;

  return (
    <div className="flex items-start gap-2">
      <input
        id={id}
        type="checkbox"
        className="mt-1 h-4 w-4 rounded border-slate-300 text-brand-600 transition-colors duration-micro ease-standard focus:ring-brand-500"
        {...props}
      />
      <div>
        <label htmlFor={id} className="block text-sm font-medium text-slate-700">
          {label}
        </label>
        {hint && <p className="text-xs text-slate-500">{hint}</p>}
      </div>
    </div>
  );
}

interface SubmitButtonProps {
  isSubmitting: boolean;
  idleLabel: string;
  busyLabel: string;
}

export function SubmitButton({ isSubmitting, idleLabel, busyLabel }: SubmitButtonProps) {
  return (
    <button
      type="submit"
      disabled={isSubmitting}
      className={`rounded-md border border-transparent bg-brand-600 py-2 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
    >
      {isSubmitting ? busyLabel : idleLabel}
    </button>
  );
}

export function FormErrorSummary({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <FadeIn role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
      {message}
    </FadeIn>
  );
}

/**
 * Success feedback after a save. The message itself is unchanged — only the
 * presentation is: a blooming flower mark plus the CSS enter animation, with
 * the live-region semantics (`role="status"`) the screen reader already relied on.
 */
export function SuccessMessage({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <div className="fc-fade-in rounded-md bg-brand-50 p-3 text-sm text-brand-900">
      <FlowerSuccess message={message} />
    </div>
  );
}

/** A value the backend controls; rendered, never editable. */
export function ReadOnlyRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-slate-100 py-2 last:border-b-0 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="text-sm font-medium text-slate-900">{value}</dd>
    </div>
  );
}
