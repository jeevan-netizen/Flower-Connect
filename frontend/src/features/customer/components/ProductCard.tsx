import { Link } from "react-router-dom";
import type { PublicProduct } from "@/features/customer/types";
import { formatAvailability, formatMoney } from "@/features/customer/format";
import { ProductImage } from "@/features/customer/components/ProductImage";
import { createOverlayState } from "@/features/customer/navigation";
import { AnimatedCard } from "@/motion/AnimatedCard";
import { PRESSABLE } from "@/motion/pressable";
import { CARD } from "@/shared/ui/classes";

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
  /** The selected delivery location, needed to serve the product image. */
  locationId?: number | null;
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
  locationId,
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
    <AnimatedCard className={`${CARD} flex p-4`}>
      <article className="flex h-full w-full flex-col gap-3">
        <Link
          to={detailPath}
          state={overlayState}
          className="block rounded-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bolder-rose focus-visible:ring-offset-2"
        >
          <ProductImage
            productId={product.id}
            image={image}
            productName={product.name}
            locationId={locationId ?? null}
          />
        </Link>

        <div className="flex-1">
          <h3 className="text-sm font-semibold text-bolder-text">
            <Link
              to={detailPath}
              state={overlayState}
              className="hover:text-bolder-rose-strong focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 rounded"
            >
              {product.name}
            </Link>
          </h3>
          <p className="mt-1 text-xs text-bolder-muted">{product.vendorName}</p>
          <p className="mt-1 text-xs text-bolder-muted">{product.categoryName}</p>
        </div>

        <div className="flex items-center justify-between gap-2">
          <span className="text-base font-semibold text-bolder-text">
            {formatMoney(product.basePrice)}
          </span>
          <span
            className={
              product.available !== null && product.available <= 3
                ? "text-xs font-medium text-bolder-warning"
                : "text-xs text-bolder-muted"
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
            className={`w-full rounded-md border border-transparent bg-bolder-rose-action px-3 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep focus:outline-none focus:ring-2 focus:ring-bolder-rose focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE}`}
          >
            {isAdding ? "Adding..." : "Add to cart"}
          </button>
        )}
      </article>
    </AnimatedCard>
  );
}
