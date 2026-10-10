import { useState } from "react";
import { Link } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import type {
  InventorySummary,
  LowStockProduct,
  StockActionKind,
  StockActionRequest,
} from "@/features/vendor/types";
import { useLowStockProducts, useStockAction, useStockMovements } from "@/features/vendor/queries";
import { LowStockBadge } from "@/features/vendor/components/CatalogBadges";
import { MovementHistoryTable } from "@/features/vendor/components/MovementHistoryTable";
import {
  STOCK_ACTION_LABELS,
  StockActionDialog,
  explainStockFailure,
} from "@/features/vendor/components/StockActionDialog";
import { CatalogLoader } from "@/features/vendor/components/ApprovedVendorGate";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { SuccessMessage } from "@/features/vendor/components/FormFields";
import { formatLocalDate } from "@/features/vendor/format";
import { toApiError } from "@/shared/lib/api-error";
import { Pagination } from "@/shared/components/Pagination";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const HEADING_CLASS = "px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500";
const CELL_CLASS = "px-4 py-3 text-sm text-slate-700 align-middle";
const ACTION_BUTTON_CLASS = `rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`;

/**
 * `LowStockProductResponse` carries the same six stock figures as
 * `InventorySummary`, so the listing row and the stock dialog can share one
 * conversion. Widening it by hand in two places would be a chance for the badge to
 * read one number and the dialog to state another.
 */
function toInventorySummary(row: LowStockProduct): InventorySummary {
  return {
    productId: row.productId,
    quantity: row.quantity,
    reservedQuantity: row.reservedQuantity,
    available: row.available,
    lowStockThreshold: row.lowStockThreshold,
    expiryDate: row.expiryDate,
    lowStock: row.lowStock,
  };
}

/** The four actions, in the order a vendor reaches for them during a shortage. */
const ROW_ACTIONS: StockActionKind[] = ["stock-in", "adjustment", "write-off", "stock-out"];

interface Selection {
  productId: number;
  productName: string;
  inventory: InventorySummary;
}

/**
 * The shop-wide inventory screen (plan task 3.9: "inventory table with stock
 * adjustments", "low stock badges", "stock in/out/adjustment/write-off dialogs",
 * "required reason validation", "movement history").
 *
 * Built on `GET /api/v1/vendors/inventory/low-stock`, which is the backend's own
 * answer to "what needs attention" — a product appears here when
 * `available ≤ lowStockThreshold`, the same condition the catalog's badge uses. The
 * screen therefore does not re-derive the list and cannot drift from it.
 *
 * The stock rules live in `StockActionDialog` and this page is its second caller, so
 * correcting a shortfall from here and from the product editor produces identical
 * validation — including which two of the four actions refuse to proceed without a
 * reason.
 *
 * Movement history is *selected*, not rendered per row: twenty products would mean
 * twenty paged queries, and only one can be read at a time. The selected row is held
 * in state rather than re-derived from the current page, because a successful write
 * can drop that product out of the low-stock list entirely — and the dialog that
 * follows still has to be able to say what the levels were.
 */
export function VendorInventoryPage() {
  const [page, setPage] = useState(0);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [movementPage, setMovementPage] = useState(0);
  const [stockAction, setStockAction] = useState<StockActionKind | null>(null);
  const [stockError, setStockError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const lowStock = useLowStockProducts(page);
  const movements = useStockMovements(selection?.productId ?? null, movementPage);
  const stockActionMutation = useStockAction();

  if (lowStock.error) {
    return <VendorErrorState error={lowStock.error} onRetry={() => void lowStock.refetch()} />;
  }

  const startAction = (row: LowStockProduct, action: StockActionKind) => {
    setSelection({
      productId: row.productId,
      productName: row.productName,
      inventory: toInventorySummary(row),
    });
    setStockError(null);
    setStockAction(action);
  };

  const runStockAction = async (payload: StockActionRequest) => {
    setStockError(null);
    setSuccess(null);
    try {
      await stockActionMutation.mutateAsync({
        productId: selection?.productId as number,
        action: stockAction as StockActionKind,
        ...payload,
      });
      setStockAction(null);
      setMovementPage(0);
      setSuccess("Stock updated and recorded in the movement history.");
    } catch (caught) {
      // The dialog stays open with the typed values intact, so the number can be
      // corrected rather than re-typed.
      setStockError(explainStockFailure(toApiError(caught), stockAction as StockActionKind));
      throw caught;
    }
  };

  const rows = lowStock.data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title="Inventory"
        description="Products at or below their low-stock threshold, with the tools to correct them."
      />

      <SuccessMessage message={success} />

      {lowStock.isPending ? (
        <CatalogLoader label="Loading your low-stock list..." />
      ) : rows.length === 0 ? (
        <Card>
          <p className="text-sm font-medium text-slate-900">Nothing is running low</p>
          <p className="mt-1 text-sm text-slate-600">
            No product is at or below its own threshold. Set a threshold on a product&apos;s stock
            settings to start tracking it here.
          </p>
        </Card>
      ) : (
        <Card>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200">
              <caption className="sr-only">Low stock products</caption>
              <thead className="bg-slate-50">
                <tr>
                  <th scope="col" className={HEADING_CLASS}>
                    Product
                  </th>
                  <th scope="col" className={HEADING_CLASS}>
                    Available
                  </th>
                  <th scope="col" className={HEADING_CLASS}>
                    Threshold
                  </th>
                  <th scope="col" className={HEADING_CLASS}>
                    Expiry
                  </th>
                  <th scope="col" className={`${HEADING_CLASS} text-right`}>
                    Stock actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((row) => (
                  <tr key={row.productId}>
                    <td className={CELL_CLASS}>
                      <Link
                        to={`/vendor/catalog/${row.productId}`}
                        className={`font-medium text-brand-700 hover:text-brand-900 ${FOCUS_RING}`}
                      >
                        {row.productName}
                      </Link>
                      <div className="mt-1">
                        <LowStockBadge inventory={toInventorySummary(row)} />
                      </div>
                    </td>
                    <td className={`${CELL_CLASS} whitespace-nowrap`}>
                      {row.available}
                      {row.reservedQuantity > 0 && (
                        <span className="text-xs text-slate-500"> ({row.reservedQuantity} reserved)</span>
                      )}
                    </td>
                    <td className={`${CELL_CLASS} whitespace-nowrap`}>{row.lowStockThreshold}</td>
                    <td className={`${CELL_CLASS} whitespace-nowrap text-slate-600`}>
                      {formatLocalDate(row.expiryDate)}
                    </td>
                    <td className={`${CELL_CLASS} text-right`}>
                      <div className="flex flex-wrap justify-end gap-2">
                        {ROW_ACTIONS.map((action) => (
                          <button
                            key={action}
                            type="button"
                            /*
                              The accessible name carries the product, not just the
                              action. A column of "Write off" buttons with identical
                              names is unusable with a screen reader — the user is told
                              "Write off, button" twenty times with no way to tell which
                              product it refers to — and it also collides with the open
                              dialog's own confirm button of the same label.
                            */
                            aria-label={`${STOCK_ACTION_LABELS[action]} stock for ${row.productName}`}
                            disabled={stockActionMutation.isPending}
                            onClick={() => startAction(row, action)}
                            className={ACTION_BUTTON_CLASS}
                          >
                            {STOCK_ACTION_LABELS[action]}
                          </button>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {lowStock.data && (
            <div className="mt-4">
              <Pagination page={lowStock.data} onPageChange={setPage} disabled={lowStock.isFetching} />
            </div>
          )}
        </Card>
      )}

      <Card
        title={selection ? `Movement history — ${selection.productName}` : "Movement history"}
      >
        {selection === null ? (
          <p className="text-sm text-slate-600">
            Choose a stock action above to read that product&apos;s movement history, or open the
            product editor for its full stock panel.
          </p>
        ) : (
          <MovementHistoryTable
            movements={movements.data}
            isPending={movements.isPending}
            onPageChange={setMovementPage}
            disabled={stockActionMutation.isPending}
          />
        )}
      </Card>

      <AnimatePresence>
        {stockAction && selection && (
          <StockActionDialog
            key={`${selection.productId}-${stockAction}`}
            action={stockAction}
            productName={selection.productName}
            inventory={selection.inventory}
            isSubmitting={stockActionMutation.isPending}
            errorMessage={stockError}
            onConfirm={runStockAction}
            onCancel={() => {
              setStockError(null);
              setStockAction(null);
            }}
          />
        )}
      </AnimatePresence>
    </div>
  );
}