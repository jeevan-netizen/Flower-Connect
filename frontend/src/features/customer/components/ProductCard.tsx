import { Link } from "react-router-dom";
import type { PublicProduct } from "@/features/customer/types";
import { formatAvailability, formatMoney } from "@/features/customer/format";
import { ProductImagePlaceholder } from "@/features/customer/components/ProductImagePlaceholder";
import { createOverlayState } from "@/features/customer/navigation";
import { AnimatedCard } from "@/motion/AnimatedCard";
import { PRESSABLE } from "@/motion/pressable";

interface ProductCardProps {
  product: PublicProduct;
  /** The florist's page, so the card can link to the product inside it. */
  vendorId?: number;
  /**
   * Where the product's detail page lives, when it is not inside the
   * product's own florist — the search surface uses `/search/products`, so a
   * product opened from the results keeps the results behind it.
   */
  detailPathBase?: string;
  /** Hides the add-to-cart control, for contexts that render their own. */
  showAddToCart?: boolean;
  onAddToCart?: (product: PublicProduct) => void;
  /** Set while an add is in flight, so the control can show it. */
  isAdding?: boolean;
}

/**
 * One product in a grid (plan tasks 4.7 and 4.8).
 *
 * The card links to the product's own page, and names its florist so a
 * customer browsing several shops can tell whose product they are looking at.
 * The add-to-cart control is rendered by the caller, because the product detail
 * page owns its own quantity and gift note while a grid card does not.
 *
 * The link carries the overlay marker, so the surface it was clicked from
 * knows to keep showing itself behind the product rather than treating the
 * product as a standalone page.
 */
export function ProductCard({
  product,
  vendorId,
  detailPathBase,
  showAddToCart = false,
  onAddToCart,
  isAdding = false,
}: ProductCardProps) {
  const detailPath = detailPathBase
    ? `${detailPathBase}/${product.id}`
    : vendorId
      ? `/vendors/${vendorId}/products/${product.id}`
      : `/vendors/${product.vendorId}/products/${product.id}`;

  // The cover is the product's primary image; the first image is the
  // fallback when none is marked primary.
  const image = product.images.find((candidate) => candidate.primary) ?? product.images[0];

  const overlayState = createOverlayState();

  return (
    <AnimatedCard className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <article className="flex h-full flex-col gap-3">
        <Link
          to={detailPath}
          state={overlayState}
          className="block focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 rounded-lg"
        >
          <ProductImagePlaceholder image={image} productName={product.name} />
        </Link>

        <div className="flex-1">
          <h3 className="text-sm font-semibold text-slate-900">
            <Link
              to={detailPath}
              state={overlayState}
              className="hover:text-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 rounded"
            >
              {product.name}
            </Link>
          </h3>
          <p className="mt-1 text-xs text-slate-500">{product.vendorName}</p>
          <p className="mt-1 text-xs text-slate-500">{product.categoryName}</p>
        </div>

        <div className="flex items-center justify-between gap-2">
          <span className="text-base font-semibold text-slate-900">
            {formatMoney(product.basePrice)}
          </span>
          <span
            className={
              product.available !== null && product.available <= 3
                ? "text-xs font-medium text-amber-700"
                : "text-xs text-slate-500"
            }
          >
            {formatAvailability(product.available)}
          </span>
        </div>

        {showAddToCart && onAddToCart && (
          <button
            type="button"
            onClick={() => onAddToCart(product)}
            disabled={isAdding || product.available === 0}
            aria-label={`Add ${product.name} to cart`}
            className={`w-full rounded-md border border-transparent bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE}`}
          >
            {isAdding ? "Adding..." : "Add to cart"}
          </button>
        )}
      </article>
    </AnimatedCard>
  );
}
