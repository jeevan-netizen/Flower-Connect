import type { StockMovementPageResponse } from "@/features/vendor/types";
import {
  formatDateTime,
  formatMovementActor,
  formatMovementType,
  formatQuantityDelta,
} from "@/features/vendor/format";
import { Pagination } from "@/shared/components/Pagination";

const HEADING_CLASS = "px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide text-slate-500";
const CELL_CLASS = "px-4 py-3 text-sm text-slate-700 align-middle";

interface MovementHistoryTableProps {
  movements: StockMovementPageResponse | undefined;
  isPending: boolean;
  onPageChange: (page: number) => void;
  /**
   * Disabled while a write invalidates the log, to avoid a paging race.
   *
   * There is deliberately no `page` prop: the pager reads `movements.page`, the index
   * the server actually returned. A separately tracked page number could disagree
   * with the fetched page — after a deletion on page 4 of 4, for instance — and the
   * pager would then offer a page the rows are not from.
   */
  disabled?: boolean;
}

/**
 * The append-only stock movement log for one product (plan task 3.6 / 3.9).
 *
 * The log is the audit trail of how a level got there (D-24), so this table is
 * deliberately complete rather than summarised: every type is named, including the
 * five a vendor cannot trigger themselves (`RESERVE`, `RELEASE`, `SALE_ONLINE`,
 * `SALE_POS`, `RESTOCK`), which arrive from checkout, the accept path and POS once
 * those phases exist.
 *
 * Two details worth stating:
 *
 *  - **The delta is signed and formatted with its sign.** A bare `5` on a stock-out
 *    row is a column the reader has to interpret against the type; `−5` is not.
 *  - **A missing actor is named, not blanked.** `actor_user_id` is `null` for the
 *    scheduled expiry sweep, which has no authenticated user (D-25), and it is
 *    labelled as such rather than attributed to whoever last touched the product.
 */
export function MovementHistoryTable({
  movements,
  isPending,
  onPageChange,
  disabled = false,
}: MovementHistoryTableProps) {
  if (isPending) {
    return <p className="text-sm text-slate-600">Loading movement history...</p>;
  }

  if (!movements || movements.content.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 p-4 text-sm text-slate-600">
        No stock movements recorded yet. Every change to this product&rsquo;s stock will appear here.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-md border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200">
          <caption className="sr-only">Stock movements</caption>
          <thead className="bg-slate-50">
            <tr>
              <th scope="col" className={HEADING_CLASS}>
                When
              </th>
              <th scope="col" className={HEADING_CLASS}>
                Type
              </th>
              <th scope="col" className={`${HEADING_CLASS} text-right`}>
                Change
              </th>
              <th scope="col" className={HEADING_CLASS}>
                Reason
              </th>
              <th scope="col" className={HEADING_CLASS}>
                Recorded by
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {movements.content.map((movement) => (
              <tr key={movement.id}>
                <td className={`${CELL_CLASS} whitespace-nowrap text-slate-500`}>
                  {formatDateTime(movement.createdAt)}
                </td>
                <td className={`${CELL_CLASS} whitespace-nowrap`}>
                  {formatMovementType(movement.movementType)}
                </td>
                <td
                  className={`${CELL_CLASS} whitespace-nowrap text-right font-medium ${
                    movement.quantityDelta < 0 ? "text-red-700" : "text-brand-700"
                  }`}
                >
                  {formatQuantityDelta(movement.quantityDelta)}
                </td>
                <td className={CELL_CLASS}>
                  {movement.reason ?? <span className="text-slate-400">—</span>}
                </td>
                <td className={`${CELL_CLASS} whitespace-nowrap text-slate-600`}>
                  {formatMovementActor(movement)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination page={movements} onPageChange={onPageChange} disabled={disabled} />
    </div>
  );
}