import { Link } from "react-router-dom";
import type { Product } from "@/features/vendor/types";
import { formatMoney } from "@/features/vendor/format";
import { LowStockBadge, ProductStatusPill, StockSummary } from "@/features/vendor/components/CatalogBadges";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const HEADING_CLASS = "px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-bolder-muted";
const CELL_CLASS = "px-4 py-3 text-sm text-bolder-text align-middle";

/**
 * The catalog listing (plan task 3.9).
 *
 * A table rather than cards, because the columns are the things a vendor compares
 * across products — price, stock, cover, lifecycle — and a card per product would
 * put each of those on its own line with no shared axis to read down. It is also
 * what "Shopify-style" means in practice for a listing: dense rows, one action
 * column.
 *
 * Every row links to the product editor, so the row itself is not a link target —
 * only the product name is. A row-sized link would swallow the checkbox columns
 * and make the accessible name of the table's real actions ambiguous.
 *
 * The low-stock badge is rendered from the backend's `InventorySummary.lowStock`
 * rather than from a comparison here, so a row can never claim to be in stock
 * while the same product sits in `GET /api/v1/vendors/inventory/low-stock`.
 */
export function ProductTable({
  products,
  isPending,
}: {
  products: Product[];
  isPending?: boolean;
}) {
  if (!isPending && products.length === 0) {
    return (
      <div className="rounded-lg border bolder-line bg-bolder-surface p-8 text-center">
        <p className="text-sm font-medium text-bolder-text">No products match these filters</p>
        <p className="mt-1 text-sm text-bolder-muted">
          Clear the filters, or add your first product to get started.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border bolder-line bg-bolder-surface shadow-sm">
      <table className="min-w-full divide-y divide-bolder-line">
        <caption className="sr-only">Your products</caption>
        <thead className="bg-bolder-bg">
          <tr>
            <th scope="col" className={HEADING_CLASS}>
              Product
            </th>
            <th scope="col" className={HEADING_CLASS}>
              Price
            </th>
            <th scope="col" className={HEADING_CLASS}>
              Stock
            </th>
            <th scope="col" className={HEADING_CLASS}>
              Status
            </th>
            <th scope="col" className={HEADING_CLASS}>
              Images
            </th>
            <th scope="col" className={`${HEADING_CLASS} text-right`}>
              Actions
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-bolder-line">
          {products.map((product) => (
            <tr key={product.id} className="transition-colors duration-micro ease-standard hover:bg-bolder-surface-muted">
              <td className={CELL_CLASS}>
                <Link
                  to={`/vendor/catalog/${product.id}`}
                  className={`font-medium text-bolder-rose-strong hover:text-bolder-rose-deep ${FOCUS_RING}`}
                >
                  {product.name}
                </Link>
                <p className="text-xs text-bolder-muted">{product.categoryName ?? "No category"}</p>
              </td>
              <td className={`${CELL_CLASS} whitespace-nowrap`}>{formatMoney(product.basePrice)}</td>
              <td className={CELL_CLASS}>
                <div className="flex flex-wrap items-center gap-2">
                  <StockSummary inventory={product.inventory} />
                  <LowStockBadge inventory={product.inventory} />
                </div>
              </td>
              <td className={CELL_CLASS}>
                <ProductStatusPill status={product.status} />
              </td>
              <td className={`${CELL_CLASS} whitespace-nowrap text-bolder-muted`}>
                {product.images.length === 0 ? "None" : product.images.length}
              </td>
              <td className={`${CELL_CLASS} text-right`}>
                <Link
                  to={`/vendor/catalog/${product.id}`}
                  className={`inline-block rounded-md border border-bolder-line-control bg-bolder-surface px-3 py-1.5 text-sm font-medium text-bolder-text hover:bg-bolder-bg ${PRESSABLE} ${FOCUS_RING}`}
                >
                  Edit
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}