import { z } from "zod";
import {
  STOCK_REASON_MAX_LENGTH,
  type ProductStatus,
} from "@/features/vendor/types";

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
/**
 * A whole number that may be negative.
 *
 * Needed because `@Digits`/`@Min` do not describe every bound here: an adjustment is
 * the one quantity the backend accepts as negative. The positive-quantity fields use
 * the unsigned pattern *and* a `> 0` refine, so "-5" on a stock-out reports "must be
 * greater than zero" rather than the misleading "must be a whole number".
 */
const SIGNED_INTEGER_PATTERN = /^-?\d+$/;

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

/* -------------------------------------------------------------------------- */
/* Catalog (plan task 3.9)                                                     */
/* -------------------------------------------------------------------------- */

/**
 * The product form's category picker.
 *
 * A `<select>` yields a string, and the backend binds `categoryId` to a `Long`, so
 * the conversion happens here rather than in the component. Written as a
 * `superRefine` for the same reason as the decimal fields above: `refine` would
 * discard the message and show zod's generic "Invalid input".
 */
export function requiredCategoryField(label = "Category") {
  return z.string().superRefine((value, ctx) => {
    if (value === "") {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `${label} is required` });
      return;
    }
    if (!INTEGER_PATTERN.test(value)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `${label} is not a valid choice` });
      return;
    }
    if (Number(value) < 1) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `${label} is not a valid choice`,
      });
    }
  });
}

/**
 * The product create/edit form.
 *
 * Every bound mirrors `ProductRequest`:
 *   name       @NotBlank, @Size(max = 160)
 *   categoryId @NotNull
 *   description @Size(max = 2000)
 *   basePrice  @NotNull, @DecimalMin(0, inclusive = false), @Digits(8, 2)
 *
 * `basePrice` is also enforced in `ProductService.requirePositivePrice`, so a zero
 * price is refused twice — here for the field message and there for any writer
 * that bypasses this form (D-12).
 */
export const productFormSchema = z.object({
  name: requiredTextField("Product name", 160),
  categoryId: requiredCategoryField(),
  description: optionalTextField("Description", 2000),
  basePrice: requiredDecimalField("Base price", {
    integerDigits: 8,
    fractionDigits: 2,
    min: 0,
    minExclusive: true,
  }),
  status: z.enum(["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"]),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;

/**
 * The fields a backend 400 can address on `ProductRequest`.
 *
 * The backend's bean-validation map is keyed by the JSON property name, so
 * `{"basePrice": "..."}` lands on the price input. A key outside this set (a
 * path variable, or a future field) is deliberately ignored rather than pushed
 * onto an unrelated input.
 */
export const PRODUCT_FORM_FIELDS = [
  "name",
  "categoryId",
  "description",
  "basePrice",
  "status",
] as const;

export function isProductFormField(field: string): field is keyof ProductFormValues {
  return (PRODUCT_FORM_FIELDS as readonly string[]).includes(field);
}

/** Form values as the product currently stored on the server. */
export function toProductFormValues(product: {
  name: string;
  categoryId: number;
  description: string | null;
  basePrice: number;
  status: ProductStatus;
}): ProductFormValues {
  return {
    name: product.name,
    categoryId: String(product.categoryId),
    description: product.description ?? "",
    basePrice: String(product.basePrice),
    status: product.status,
  };
}

/* -------------------------------------------------------------------------- */
/* Stock actions (plan task 3.6)                                               */
/* -------------------------------------------------------------------------- */

/**
 * Quantity for `stock-in`, `stock-out` and `write-off`.
 *
 * All three are `@NotNull` + `@Positive` on the DTO and are re-checked in
 * `InventoryService.requirePositive`, so a zero or negative add is refused twice.
 */
export const positiveQuantityField = z
  .string()
  .trim()
  .min(1, "Quantity is required")
  .refine((value) => SIGNED_INTEGER_PATTERN.test(value), "Quantity must be a whole number")
  .refine((value) => Number(value) > 0, "Quantity must be greater than zero");

/**
 * Quantity for `adjustment`: a **signed, non-zero** correction.
 *
 * Deliberately not `positiveQuantityField`. `StockAdjustmentRequest.quantity` is
 * only `@NotNull`, and `InventoryService.requireNonZero` rejects exactly one
 * value — `0` — because an `ADJUSTMENT` movement with a zero delta would be a
 * log row recording no change, which the append-only movement log is not meant to
 * hold (D-24).
 */
export const signedQuantityField = z
  .string()
  .trim()
  .min(1, "Quantity is required")
  .refine((value) => SIGNED_INTEGER_PATTERN.test(value), "Quantity must be a whole number")
  .refine((value) => Number(value) !== 0, "Adjustment must change the quantity by at least 1");

/** Optional reason for `stock-in` / `stock-out`: `@Size(max = 500)`, no `@NotBlank`. */
export const optionalStockReasonField = z
  .string()
  .trim()
  .max(STOCK_REASON_MAX_LENGTH, `Reason must not exceed ${STOCK_REASON_MAX_LENGTH} characters`);

/**
 * Mandatory reason for `adjustment` / `write-off`: `@NotBlank` + `@Size(max = 500)`.
 *
 * `reasonLabel` names the action ("a stock adjustment" / "a write-off") because
 * the same dialog serves both, and a bare "Reason is required" leaves the vendor
 * guessing which rule they hit. The wording mirrors the backend's own
 * `@NotBlank` messages so the two agree.
 */
export function requiredStockReasonField(reasonLabel: string) {
  return z
    .string()
    .trim()
    .min(1, `A reason is required for ${reasonLabel}`)
    .max(STOCK_REASON_MAX_LENGTH, `Reason must not exceed ${STOCK_REASON_MAX_LENGTH} characters`);
}

export const stockInSchema = z.object({
  quantity: positiveQuantityField,
  reason: optionalStockReasonField,
});

export const adjustmentSchema = z.object({
  quantity: signedQuantityField,
  reason: requiredStockReasonField("a stock adjustment"),
});

export const writeOffSchema = z.object({
  quantity: positiveQuantityField,
  reason: requiredStockReasonField("a write-off"),
});

export type StockInFormValues = z.infer<typeof stockInSchema>;
export type AdjustmentFormValues = z.infer<typeof adjustmentSchema>;
export type WriteOffFormValues = z.infer<typeof writeOffSchema>;

/** The `low_stock_threshold` and `expiry_date` settings on the inventory panel. */
export const lowStockThresholdSchema = z.object({
  lowStockThreshold: z
    .string()
    .trim()
    .min(1, "Low stock threshold is required")
    .refine((value) => INTEGER_PATTERN.test(value), "Low stock threshold must be a whole number")
    .refine((value) => Number(value) >= 0, "Low stock threshold must not be negative"),
});

export type LowStockThresholdFormValues = z.infer<typeof lowStockThresholdSchema>;
