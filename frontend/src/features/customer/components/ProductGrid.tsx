import type { PublicProduct } from "@/features/customer/types";
import { ProductCard } from "@/features/customer/components/ProductCard";

interface ProductGridProps {
  products: PublicProduct[];
  /** The florist's page, so each card can link to its product. */
  vendorId?: number;
  /**
   * Where the product's detail page lives, when it is not inside the
   * product's own florist — the search surface uses `/search/products`.
   */
  detailPathBase?: string;
  showAddToCart?: boolean;
  onAddToCart?: (product: PublicProduct) => void;
  isAdding?: boolean;
  /** Rendered instead of the grid when there is nothing to show. */
  emptyMessage?: string;
}

/**
 * A responsive grid of product cards.
 *
 * One column on a phone, widening to four on a wide screen. The grid is a
 * list, so a screen reader announces how many products there are before it
 * starts reading them.
 */
export function ProductGrid({
  products,
  vendorId,
  detailPathBase,
  showAddToCart = false,
  onAddToCart,
  isAdding = false,
  emptyMessage = "No products to show yet.",
}: ProductGridProps) {
  if (products.length === 0) {
    return <p className="text-sm text-slate-600">{emptyMessage}</p>;
  }

  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {products.map((product) => (
        <li key={product.id}>
          <ProductCard
            product={product}
            vendorId={vendorId}
            detailPathBase={detailPathBase}
            showAddToCart={showAddToCart}
            onAddToCart={onAddToCart}
            isAdding={isAdding}
          />
        </li>
      ))}
    </ul>
  );
}
