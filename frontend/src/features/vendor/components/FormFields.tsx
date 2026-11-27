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
const CONTROL_CLASS = `mt-1 block w-full rounded-md border-bolder-line-control shadow-sm ${FIELD_TRANSITION} focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm`;
const ERROR_BORDER = "border-bolder-danger-border";

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
      <label htmlFor={id} className="block text-sm font-medium text-bolder-text">
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
      <label htmlFor={id} className="block text-sm font-medium text-bolder-text">
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
          className="mt-1 h-4 w-4 rounded border-bolder-line-control text-bolder-rose transition-colors duration-micro ease-standard focus:ring-bolder-rose"
          {...props}
        />
      <div>
        <label htmlFor={id} className="block text-sm font-medium text-bolder-text">
          {label}
        </label>
        {hint && <p className="text-xs text-bolder-muted">{hint}</p>}
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
      className={`rounded-md border border-transparent bg-bolder-rose-action py-2 px-4 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${PRESSABLE}`}
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
    <FadeIn role="alert" className="rounded-md bg-bolder-danger-surface p-3 text-sm text-bolder-danger">
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
    <div className="fc-fade-in rounded-md bg-bolder-blush-light p-3 text-sm text-bolder-rose-deep">
      <FlowerSuccess message={message} />
    </div>
  );
}

/** A value the backend controls; rendered, never editable. */
export function ReadOnlyRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b bolder-line py-2 last:border-b-0 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <dt className="text-sm text-bolder-muted">{label}</dt>
      <dd className="text-sm font-medium text-bolder-text">{value}</dd>
    </div>
  );
}
