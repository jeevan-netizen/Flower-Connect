import { z } from "zod";

/**
 * Form-value schemas for the vendor area.
 *
 * Two rules make these consistent with the backend:
 *
 * 1. Form values are kept as **strings** and converted only when the PUT payload
 *    is built. That keeps `""` distinguishable from `0` (so clearing an optional
 *    fee sends `null`, not `0`) and avoids `NaN` from `valueAsNumber` on an empty
 *    number input.
 * 2. Every bound mirrors the matching `@DecimalMin` / `@Digits` / `@Min` /
 *    `@Max` on `VendorProfileUpdateRequest`. Client validation is a fast
 *    convenience only — the backend re-checks everything and stays authoritative.
 */

const DECIMAL_PATTERN = /^\d+(\.\d+)?$/;
const INTEGER_PATTERN = /^\d+$/;

function fractionDigitsOf(value: string): number {
  const dot = value.indexOf(".");
  return dot === -1 ? 0 : value.length - dot - 1;
}

function integerDigitsOf(value: string): number {
  const dot = value.indexOf(".");
  const whole = dot === -1 ? value : value.slice(0, dot);
  return whole.replace(/^0+(?=\d)/, "").length;
}

interface DecimalFieldOptions {
  integerDigits: number;
  fractionDigits: number;
  min?: number;
  max?: number;
  minExclusive?: boolean;
}

function decimalChecks(label: string, options: DecimalFieldOptions) {
  return (value: string): string | true => {
    if (!DECIMAL_PATTERN.test(value)) {
      return `${label} must be a number`;
    }
    if (integerDigitsOf(value) > options.integerDigits) {
      return `${label} must have at most ${options.integerDigits} integer digits`;
    }
    if (fractionDigitsOf(value) > options.fractionDigits) {
      return `${label} must have at most ${options.fractionDigits} decimal places`;
    }
    const numeric = Number(value);
    if (options.min !== undefined) {
      if (options.minExclusive ? numeric <= options.min : numeric < options.min) {
        return options.minExclusive
          ? `${label} must be greater than ${options.min}`
          : `${label} must not be less than ${options.min}`;
      }
    }
    if (options.max !== undefined && numeric > options.max) {
      return `${label} must not exceed ${options.max}`;
    }
    return true;
  };
}

export function requiredTextField(label: string, max: number) {
  return z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .max(max, `${label} must not exceed ${max} characters`);
}

/** Optional single-line text. Blank is legal and is sent as `null`. */
export function optionalTextField(label: string, max: number) {
  return z.string().trim().max(max, `${label} must not exceed ${max} characters`);
}

export function requiredDecimalField(label: string, options: DecimalFieldOptions) {
  // `superRefine` rather than `refine`: zod v3 ignores the string a `refine`
  // check returns and would surface its default "Invalid input" instead of the
  // bound that was actually violated.
  return z.string().trim().superRefine((value, ctx) => {
    if (value === "") {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `${label} is required` });
      return;
    }
    const result = decimalChecks(label, options)(value);
    if (result !== true) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: result });
    }
  });
}

/** Optional money-like decimal. Blank is legal and is sent as `null`. */
export function optionalDecimalField(label: string, options: DecimalFieldOptions) {
  return z.string().trim().superRefine((value, ctx) => {
    if (value === "") {
      return;
    }
    if (!DECIMAL_PATTERN.test(value)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `${label} must be a number` });
      return;
    }
    const result = decimalChecks(label, options)(value);
    if (result !== true) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: result });
    }
  });
}

export function requiredIntegerField(label: string, min: number, max: number) {
  return z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .refine((value) => INTEGER_PATTERN.test(value), `${label} must be a whole number`)
    .refine((value) => {
      const numeric = Number(value);
      return numeric >= min && numeric <= max;
    }, `${label} must be between ${min} and ${max}`);
}

/** `""` → `null`, so an unset optional fee is not confused with `0`. */
export function toNumber(value: string): number {
  return Number(value.trim());
}

export function toNumberOrNull(value: string): number | null {
  return value.trim() === "" ? null : Number(value.trim());
}

export function toTextOrNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

/** Logo URLs are stored as a plain `VARCHAR(512)`; accept absolute or root-relative paths. */
export const logoUrlField = z
  .string()
  .trim()
  .max(512, "Logo URL must not exceed 512 characters")
  .refine(
    (value) => value === "" || /^(https?:\/\/|\/)/.test(value),
    "Logo URL must start with http://, https:// or /",
  );
