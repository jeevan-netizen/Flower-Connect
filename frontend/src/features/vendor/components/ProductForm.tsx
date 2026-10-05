import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Category, Product, ProductRequest, ProductStatus } from "@/features/vendor/types";
import { PRODUCT_STATUSES } from "@/features/vendor/types";
import {
  isProductFormField,
  productFormSchema,
  toNumber,
  toProductFormValues,
  toTextOrNull,
  type ProductFormValues,
} from "@/features/vendor/form-schema";
import { formatProductStatus, PRODUCT_STATUS_HINTS } from "@/features/vendor/format";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import {
  NumberField,
  SubmitButton,
  TextField,
} from "@/features/vendor/components/FormFields";

const MAX_DESCRIPTION = 2000;
const CONTROL_CLASS =
  "mt-1 block w-full rounded-md border-slate-300 shadow-sm transition-[border-color,box-shadow] duration-micro ease-standard focus:border-brand-500 focus:ring-brand-500 sm:text-sm";

interface ProductFormProps {
  /** The product being edited, or `null` when creating a new one. */
  product: Product | null;
  categories: Category[];
  isSubmitting: boolean;
  /**
   * Performs the write. Must **reject** on failure — this form maps the rejection
   * onto the individual inputs, which is only possible if the error reaches here
   * rather than being swallowed by the caller.
   */
  onSubmit: (payload: ProductRequest) => Promise<void>;
  /** Receives the normalised failure so the page can own its summary banner. */
  onError: (error: ApiErrorInfo) => void;
}

/**
 * The product create/edit form (plan task 3.9, "Shopify-style").
 *
 * "Shopify-style" here means the *ordering and the honesty of the form*, not a new
 * design language: everything still composes the vendor area's existing `Card`,
 * `TextField`, `NumberField` and `SubmitButton`, so the screen matches the rest of
 * the product. The usability rules borrowed are the ones that matter to a merchant
 * editing an item all day:
 *
 *  - **Title first, at full weight.** The name is the only field in every listing,
 *    so it leads and is never collapsed behind an "advanced" toggle.
 *  - **One column for prose, a row for the paired facts.** Category and price are
 *    read together; the description gets its own block because it is written, not
 *    scanned.
 *  - **The status control explains itself.** No product-status matrix is enforced
 *    server-side (D-22), so the select renders the chosen status's meaning inline
 *    instead of implying which values are legal from which others.
 *  - **A live character counter on the description**, because `@Size(max = 2000)`
 *    is otherwise only discovered by being rejected.
 *  - **The slug is shown, never offered.** It is server-generated (D-19), so
 *    editing it is impossible by design — displaying it tells a vendor where their
 *    product will live in a URL without pretending they chose it.
 *
 * Backend validation errors are attached to the matching input whenever the server
 * addressed a field, so a `400 VALIDATION_FAILED` reads as a field error rather
 * than a page-level failure. A failure the server could not attribute to a field —
 * `Category is not active`, or `Base price must be greater than zero` raised by
 * `ProductService.requirePositivePrice` — is handed to `onError` and rendered
 * verbatim above the fields, never replaced with a generic message.
 */
export function ProductForm({
  product,
  categories,
  isSubmitting,
  onSubmit,
  onError,
}: ProductFormProps) {
  const isEdit = product !== null;

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: {
      name: "",
      categoryId: "",
      description: "",
      basePrice: "",
      status: "DRAFT",
    },
  });

  // Re-seed from the product whenever it changes. `reset` rather than `setValue`
  // so validation state left over from a previous submit is cleared with the
  // values it belonged to.
  useEffect(() => {
    if (product) {
      reset(toProductFormValues(product));
    }
  }, [product, reset]);

  const description = watch("description");
  const status = watch("status");

  const submit = handleSubmit(async (values) => {
    const payload: ProductRequest = {
      name: values.name,
      categoryId: Number(values.categoryId),
      description: toTextOrNull(values.description),
      basePrice: toNumber(values.basePrice),
      status: values.status as ProductStatus,
    };

    try {
      await onSubmit(payload);
    } catch (caught) {
      const info = toApiError(caught);
      Object.entries(info.validation).forEach(([field, message]) => {
        if (isProductFormField(field)) {
          setError(field, { type: "server", message });
        }
      });
      onError(info);
      // Swallowed deliberately: `onError` has already been told about it, and
      // rethrowing from a form submit surfaces as an unhandled rejection.
    }
  });

  return (
    <form className="space-y-5" onSubmit={submit} noValidate>
      <TextField
        label="Product name"
        hint={
          isEdit
            ? "Changing the name regenerates the product's URL slug on the server."
            : "This is the name customers see in search results and on the storefront."
        }
        error={errors.name?.message}
        registration={register("name")}
      />

      <div>
        <label htmlFor="product-category" className="block text-sm font-medium text-slate-700">
          Category
        </label>
        <select
          id="product-category"
          aria-invalid={errors.categoryId ? true : undefined}
          aria-describedby="product-category-hint"
          className={`${CONTROL_CLASS} ${errors.categoryId ? "border-red-500" : ""}`}
          {...register("categoryId")}
        >
          <option value="">Choose a category</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        <p id="product-category-hint" className="mt-1 text-xs text-slate-500">
          Only active categories are listed, and a product must sit in one.
        </p>
        {errors.categoryId?.message && (
          <p className="mt-1 text-xs text-red-600">{errors.categoryId.message}</p>
        )}
      </div>

      <NumberField
        label="Base price (₹)"
        step="0.01"
        min="0"
        hint="Prices are tax-inclusive in v1 (D-3). Must be greater than zero."
        error={errors.basePrice?.message}
        registration={register("basePrice")}
      />

      <div>
        <label htmlFor="product-status" className="block text-sm font-medium text-slate-700">
          Status
        </label>
        <select
          id="product-status"
          aria-describedby="product-status-hint"
          className={CONTROL_CLASS}
          {...register("status")}
        >
          {PRODUCT_STATUSES.map((option) => (
            <option key={option} value={option}>
              {formatProductStatus(option)}
            </option>
          ))}
        </select>
        <p id="product-status-hint" className="mt-1 text-xs text-slate-500">
          {PRODUCT_STATUS_HINTS[status]}
        </p>
      </div>

      <div>
        <label htmlFor="product-description" className="block text-sm font-medium text-slate-700">
          Description
        </label>
        <textarea
          id="product-description"
          rows={6}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby="product-description-counter"
          className={`${CONTROL_CLASS} ${errors.description ? "border-red-500" : ""}`}
          {...register("description")}
        />
        <p
          id="product-description-counter"
          className={`mt-1 text-xs ${
            description.length > MAX_DESCRIPTION ? "text-red-600" : "text-slate-500"
          }`}
        >
          {description.length} / {MAX_DESCRIPTION} characters. Leave blank for no description.
        </p>
        {errors.description?.message && (
          <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
        )}
      </div>

      {isEdit && product && (
        <dl className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
          <div className="flex gap-2">
            <dt className="font-medium text-slate-700">Product id</dt>
            <dd>{product.id}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-medium text-slate-700">Slug</dt>
            <dd className="truncate">{product.slug}</dd>
          </div>
          <p className="mt-2">
            The slug is generated from the name on the server, so it follows your product and is never
            typed by hand.
          </p>
        </dl>
      )}

      <SubmitButton
        isSubmitting={isSubmitting}
        idleLabel={isEdit ? "Save changes" : "Create product"}
        busyLabel="Saving..."
      />
    </form>
  );
}