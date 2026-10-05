import { Link } from "react-router-dom";
import type { Product } from "@/features/vendor/types";
import { formatMoney } from "@/features/vendor/format";
import { LowStockBadge, ProductStatusPill, StockSummary } from "@/features/vendor/components/CatalogBadges";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const HEADING_CLASS = "px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500";
const CELL_CLASS = "px-4 py-3 text-sm text-slate-700 align-middle";

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
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center">
        <p className="text-sm font-medium text-slate-900">No products match these filters</p>
        <p className="mt-1 text-sm text-slate-600">
          Clear the filters, or add your first product to get started.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200">
        <caption className="sr-only">Your products</caption>
        <thead className="bg-slate-50">
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
        <tbody className="divide-y divide-slate-100">
          {products.map((product) => (
            <tr key={product.id} className="transition-colors duration-micro ease-standard hover:bg-slate-50">
              <td className={CELL_CLASS}>
                <Link
                  to={`/vendor/catalog/${product.id}`}
                  className={`font-medium text-brand-700 hover:text-brand-900 ${FOCUS_RING}`}
                >
                  {product.name}
                </Link>
                <p className="text-xs text-slate-500">{product.categoryName ?? "No category"}</p>
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
              <td className={`${CELL_CLASS} whitespace-nowrap text-slate-600`}>
                {product.images.length === 0 ? "None" : product.images.length}
              </td>
              <td className={`${CELL_CLASS} text-right`}>
                <Link
                  to={`/vendor/catalog/${product.id}`}
                  className={`inline-block rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING}`}
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