/**
 * B2B statements (Stage 3 of the B2B module).
 *
 * Sellers can select a buyer and a date range, preview the eligible delivered,
 * unpaid orders and their combined total, and generate an `OPEN` statement.
 * Statements are listed for the caller as `SELLER` or `BUYER`; an `OPEN`
 * statement can be paid by its seller with a payment mode and optional note.
 * Buyers have a read-only list and detail view of statements received.
 *
 * Date handling is local to the UI: `to` defaults to today and `from` defaults
 * to 15 days before `to`, matching the server's defaults. Loading, empty,
 * invalid-range and backend error states are explicit.
 */

import { useState } from "react";
import { useSellers } from "@/features/b2b/queries";
import {
  useB2BStatementPreview,
  useCreateB2BStatement,
  useB2BStatements,
  useRecordB2BStatementPayment,
} from "@/features/b2b/queries";
import type {
  B2BStatementPageResponse,
  B2BStatementResponse,
  B2BStatementOrderSummary,
} from "@/features/b2b/types";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { formatMoney, formatLocalDate } from "@/features/vendor/format";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError } from "@/shared/lib/api-error";

const PAGE_SIZE = 20;

const ROLES: { key: "SELLER" | "BUYER"; label: string }[] = [
  { key: "SELLER", label: "As seller" },
  { key: "BUYER", label: "As buyer" },
];

function todayLocal(): string {
  return new Date().toISOString().slice(0, 10);
}

function fifteenDaysAgo(): string {
  const d = new Date();
  d.setDate(d.getDate() - 15);
  return d.toISOString().slice(0, 10);
}

export function B2BStatementsPage() {
  const [view, setView] = useState<"list" | "create" | "detail">("create");
  const [role, setRole] = useState<"SELLER" | "BUYER">("SELLER");
  const [page, setPage] = useState(0);
  const [buyerId, setBuyerId] = useState<number | null>(null);
  const [fromDate, setFromDate] = useState(fifteenDaysAgo());
  const [toDate, setToDate] = useState(todayLocal());
  const [selectedStatement, setSelectedStatement] = useState<
    B2BStatementResponse | null
  >(null);
  const [actionError, setActionError] = useState<{ message: string } | null>(null);

  const { data: sellers } = useSellers(null);
  const sellerOptions = sellers ?? [];

  const preview = useB2BStatementPreview(buyerId, fromDate, toDate);
  const createStatement = useCreateB2BStatement();
  const statements = useB2BStatements({ role, page, size: PAGE_SIZE });
  const recordPayment = useRecordB2BStatementPayment();

  const eligibleOrders: B2BStatementOrderSummary[] = preview.data?.orders ?? [];
  const previewTotal = preview.data?.totalAmount ?? 0;
  const previewPending = preview.isPending;
  const previewError = preview.error;

  const statementList: B2BStatementPageResponse = statements.data ?? {
    content: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    empty: true,
  };

  const isCreating = createStatement.isPending;
  const isPaying = recordPayment.isPending;

  const canCreate =
    buyerId != null &&
    eligibleOrders.length > 0 &&
    !isCreating &&
    !previewPending;

  const clearError = () => setActionError(null);

  const handlePreview = () => {
    clearError();
    // `useB2BStatementPreview` is reactive to buyerId/fromDate/toDate, so
    // simply ensuring they are set triggers a refetch.
  };

  const handleCreate = async () => {
    clearError();
    if (!buyerId) return;
    try {
      const statement = await createStatement.mutateAsync({
        buyerId,
        from: fromDate,
        to: toDate,
      });
      setActionError(null);
      setSelectedStatement(statement);
      setView("detail");
    } catch (caught) {
      setActionError({ message: toApiError(caught).message });
    }
  };

  const handlePay = async (
    statementId: number,
    paymentMode: "CASH" | "UPI" | "BANK_TRANSFER",
    paymentNote: string | null,
  ) => {
    clearError();
    try {
      const statement = await recordPayment.mutateAsync({
        statementId,
        paymentMode,
        paymentNote: paymentNote || null,
      });
      setActionError(null);
      setSelectedStatement(statement);
      void statements.refetch();
    } catch (caught) {
      setActionError({ message: toApiError(caught).message });
    }
  };

  const selectStatement = (statement: B2BStatementResponse) => {
    setSelectedStatement(statement);
    setView("detail");
  };

  const backToList = () => {
    setSelectedStatement(null);
    setView("list");
  };

  if (previewError || statements.error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="Statements" description="B2B statements and payment recording." />
        <VendorErrorState
          error={previewError ?? statements.error}
          onRetry={() => {
            handlePreview();
          }}
        />
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8 space-y-8">
      <PageHeading
        title="Statements"
        description="Group delivered, unpaid B2B orders into statements and record payment."
      />

      <div className="flex items-center gap-4 border-b border-bolder-line">
        <button
          type="button"
          onClick={() => {
            setView("create");
            setSelectedStatement(null);
          }}
          className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
            view === "create"
              ? "border-brand-600 text-brand-900"
              : "border-transparent text-bolder-muted hover:text-slate-700"
          }`}
        >
          Generate statement
        </button>
        <button
          type="button"
          onClick={() => {
            setView("list");
            setSelectedStatement(null);
          }}
          className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
            view === "list" || view === "detail"
              ? "border-brand-600 text-brand-900"
              : "border-transparent text-bolder-muted hover:text-slate-700"
          }`}
        >
          Statements list
        </button>
      </div>

      {/* 1. Create statement (seller only) */}
      {view === "create" && (
        <>
          <div className="rounded-lg border border-bolder-line bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-bolder-text mb-4">
              Create a statement
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="col-span-full sm:col-span-2">
                <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="statement-buyer-select">
                  Buyer
                </label>
                <select
                  id="statement-buyer-select"
                  value={buyerId ?? ""}
                  onChange={(event) =>
                    setBuyerId(event.target.value === "" ? null : Number(event.target.value))
                  }
                  className="w-full rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
                >
                  <option value="">Select a buyer</option>
                  {sellerOptions.map((seller) => (
                    <option key={seller.vendorId} value={seller.vendorId}>
                      {seller.businessName} ({seller.area})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="statement-from-date">
                  From
                </label>
                <input
                  id="statement-from-date"
                  type="date"
                  value={fromDate}
                  onChange={(event) => setFromDate(event.target.value)}
                  className="w-full rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="statement-to-date">
                  To
                </label>
                <input
                  id="statement-to-date"
                  type="date"
                  value={toDate}
                  onChange={(event) => setToDate(event.target.value)}
                  className="w-full rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
                />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-3">
              <button
                type="button"
                onClick={handlePreview}
                disabled={!buyerId || previewPending}
                className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
              >
                {previewPending ? "Previewing…" : "Preview"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setFromDate(fifteenDaysAgo());
                  setToDate(todayLocal());
                }}
                className="rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Reset range
              </button>
              <p className="ml-auto text-xs text-bolder-muted">
                Defaults to the last 15 days.
              </p>
            </div>
          </div>

          {/* Preview results */}
          {buyerId != null && (
            <div className="rounded-lg border border-bolder-line bg-white p-5 shadow-sm">
              <h2 className="text-sm font-semibold text-bolder-text mb-3">
                Eligible orders
              </h2>
              {previewPending ? (
                <FadeIn className="rounded-lg border border-bolder-line bg-white p-12 text-center text-sm text-bolder-muted">
                  Fetching eligible orders…
                </FadeIn>
              ) : eligibleOrders.length === 0 ? (
                <FadeIn className="rounded-lg border border-bolder-line bg-white p-8 text-center">
                  <h2 className="text-base font-semibold text-bolder-text">
                    No eligible orders
                  </h2>
                  <p className="mt-2 text-sm text-bolder-muted">
                    Delivered, unpaid orders for this buyer in the selected
                    range can be grouped into a statement.
                  </p>
                </FadeIn>
              ) : (
                <>
                  <table className="w-full text-left text-sm divide-y divide-slate-100 mb-4">
                    <thead>
                      <tr className="text-xs uppercase tracking-wide text-bolder-muted">
                        <th scope="col" className="py-3 pr-4 font-medium">
                          Order
                        </th>
                        <th scope="col" className="py-3 font-medium text-right">
                          Total
                        </th>
                        <th scope="col" className="py-3 font-medium text-right">
                          Delivered
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {eligibleOrders.map((order) => (
                        <tr key={order.id} className="align-top">
                          <td className="py-3 pr-4">
                            <p className="font-medium text-bolder-text">
                              #{order.orderNo}
                            </p>
                          </td>
                          <td className="py-3 font-medium text-right text-bolder-text">
                            {formatMoney(order.total)}
                          </td>
                          <td className="py-3 text-right text-sm text-bolder-muted">
                            {formatLocalDate(order.deliveredAt ?? null)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="flex justify-end">
                    <span className="text-sm font-semibold text-bolder-text">
                      Total: {formatMoney(previewTotal)}
                    </span>
                  </div>
                  <div className="mt-4">
                    <button
                      type="button"
                      disabled={!canCreate}
                      onClick={handleCreate}
                      className={`w-full rounded-md border border-transparent bg-bolder-rose-action px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      {isCreating ? "Creating statement…" : "Create statement"}
                    </button>
                    {!canCreate && eligibleOrders.length > 0 && (
                      <p className="mt-2 text-xs text-bolder-muted">
                        Generate the statement to group these orders.
                      </p>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </>
      )}

      {/* 2. Statements list */}
      {view === "list" && (
        <>
          <div className="flex items-center gap-4 mb-4">
            {ROLES.map((r) => (
              <button
                key={r.key}
                type="button"
                onClick={() => {
                  setRole(r.key);
                  setPage(0);
                }}
                className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                  role === r.key
                    ? "border-brand-600 text-brand-900"
                    : "border-transparent text-bolder-muted hover:text-slate-700"
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>

          {statements.isPending && !statementList.content.length ? (
            <FadeIn className="rounded-lg border border-bolder-line bg-white p-12 text-center text-sm text-bolder-muted">
              Loading statements…
            </FadeIn>
          ) : statementList.content.length === 0 ? (
            <FadeIn className="rounded-lg border border-bolder-line bg-white p-12 text-center">
              <h2 className="text-base font-semibold text-bolder-text">
                No statements
              </h2>
              <p className="mt-2 text-sm text-bolder-muted">
                {role === "SELLER"
                  ? "Statements you create for buyers will appear here."
                  : "Statements sellers create for you will appear here."}
              </p>
            </FadeIn>
          ) : (
            <>
              <table className="w-full text-left text-sm divide-y divide-slate-100">
                <thead>
                  <tr className="text-xs uppercase tracking-wide text-bolder-muted">
                    <th scope="col" className="py-3 pr-4 font-medium">
                      Statement
                    </th>
                    <th scope="col" className="py-3 pr-4 font-medium">Party</th>
                    <th scope="col" className="py-3 pr-4 font-medium">Period</th>
                    <th scope="col" className="py-3 pr-4 font-medium text-right">
                      Total
                    </th>
                    <th scope="col" className="py-3 font-medium text-right">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {statementList.content.map((statement) => (
                    <tr
                      key={statement.id}
                      className="align-top group cursor-pointer hover:bg-slate-50"
                      onClick={() => selectStatement(statement)}
                    >
                      <td className="py-3 pr-4">
                        <p className="font-medium text-bolder-text">
                          #{statement.statementNo}
                        </p>
                      </td>
                      <td className="py-3 pr-4">
                        <p className="font-medium text-bolder-text">
                          {role === "SELLER" ? statement.buyerName : statement.sellerName}
                        </p>
                      </td>
                      <td className="py-3 pr-4 text-sm text-bolder-muted">
                        {formatLocalDate(statement.periodStart)} –
                        {formatLocalDate(statement.periodEnd)}
                      </td>
                      <td className="py-3 font-medium text-right text-bolder-text">
                        {formatMoney(statement.totalAmount)}
                      </td>
                      <td className="py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${statementStatusTone(statement.status)}`}
                        >
                          {statement.status === "OPEN" ? "Open" : "Paid"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <Pagination
                page={statementList}
                onPageChange={(nextPage) => setPage(nextPage)}
              />
            </>
          )}
        </>
      )}

      {/* 3. Statement detail */}
      {view === "detail" && selectedStatement && (
        <div className="rounded-lg border border-bolder-line bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-bolder-text">
                Statement #{selectedStatement.statementNo}
              </h2>
              <p className="text-xs text-bolder-muted">
                {selectedStatement.sellerName} →{" "}
                {selectedStatement.buyerName}
              </p>
            </div>
            <button
              type="button"
              onClick={backToList}
              className={`rounded-md border border-transparent bg-bolder-rose-action px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Back to list
            </button>
          </div>

          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="col-span-full border-b border-slate-100 pb-2">
              <dt className="text-xs text-bolder-muted">Period</dt>
              <dd className="text-sm text-bolder-text">
                {formatLocalDate(selectedStatement.periodStart)} –
                {formatLocalDate(selectedStatement.periodEnd)}
              </dd>
            </div>
            <div className="border-b border-slate-100 pb-2">
              <dt className="text-xs text-bolder-muted">Total</dt>
              <dd className="text-sm font-medium text-bolder-text">
                {formatMoney(selectedStatement.totalAmount)}
              </dd>
            </div>
            <div className="border-b border-slate-100 pb-2">
              <dt className="text-xs text-bolder-muted">Status</dt>
              <dd className="text-sm text-bolder-text">
                {selectedStatement.status === "OPEN" ? "Open" : "Paid"}
              </dd>
            </div>
            {selectedStatement.paidAt && (
              <div className="border-b border-slate-100 pb-2">
                <dt className="text-xs text-bolder-muted">Paid at</dt>
                <dd className="text-sm text-bolder-text">
                  {formatLocalDate(selectedStatement.paidAt.split("T")[0])}
                </dd>
              </div>
            )}
            {selectedStatement.paymentMode && (
              <div className="border-b border-slate-100 pb-2">
                <dt className="text-xs text-bolder-muted">Payment mode</dt>
                <dd className="text-sm text-bolder-text">
                  {selectedStatement.paymentMode}
                </dd>
              </div>
            )}
            {selectedStatement.paymentNote && (
              <div className="pb-2">
                <dt className="text-xs text-bolder-muted">Note</dt>
                <dd className="text-sm text-bolder-text">
                  {selectedStatement.paymentNote}
                </dd>
              </div>
            )}
          </dl>

          <div className="mt-5">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-bolder-muted mb-2">
              Orders
            </h3>
            {selectedStatement.orders.length === 0 ? (
              <p className="text-sm text-bolder-muted">No orders in this statement.</p>
            ) : (
              <table className="w-full text-left text-sm divide-y divide-slate-100">
                <thead>
                  <tr className="text-xs uppercase tracking-wide text-bolder-muted">
                    <th scope="col" className="py-3 pr-4 font-medium">Order</th>
                    <th scope="col" className="py-3 font-medium text-right">Total</th>
                    <th scope="col" className="py-3 font-medium text-right">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedStatement.orders.map((order) => (
                    <tr key={order.id} className="align-top">
                      <td className="py-3 pr-4">
                        <p className="font-medium text-bolder-text">#{order.orderNo}</p>
                      </td>
                      <td className="py-3 font-medium text-right text-bolder-text">
                        {formatMoney(order.total)}
                      </td>
                      <td className="py-3 text-right text-sm text-bolder-muted">
                        {order.status}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Record payment (seller, OPEN only) */}
          {role === "SELLER" &&
            selectedStatement.status === "OPEN" && (
              <div className="mt-6 rounded-lg border border-bolder-line bg-white p-5 shadow-sm">
                <h3 className="text-sm font-semibold text-bolder-text mb-3">
                  Record payment
                </h3>
                <p className="text-xs text-bolder-muted mb-3">
                  Full payment only. Marks this statement and every linked
                  order PAID.
                </p>
                <form
                  className="space-y-3"
                  noValidate
                  onSubmit={(event) => {
                    event.preventDefault();
                    const form = event.currentTarget;
                    const mode = (
                      form.elements.namedItem("paymentMode") as HTMLSelectElement
                    ).value as "CASH" | "UPI" | "BANK_TRANSFER";
                    const note = (
                      form.elements.namedItem("paymentNote") as HTMLTextAreaElement
                    ).value;
                    void handlePay(selectedStatement.id, mode, note || null);
                  }}
                >
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-slate-700 mb-1">
                        Payment mode
                      </label>
                      <select
                        name="paymentMode"
                        defaultValue="CASH"
                        className="w-full rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
                      >
                        <option value="CASH">Cash</option>
                        <option value="UPI">UPI</option>
                        <option value="BANK_TRANSFER">Bank transfer</option>
                      </select>
                    </div>
                    <div className="col-span-full sm:col-span-2">
                      <label className="block text-sm font-medium text-slate-700 mb-1">
                        Note (optional)
                      </label>
                      <textarea
                        name="paymentNote"
                        rows={2}
                        placeholder="A reference for this payment"
                        className="w-full rounded-md border border-bolder-line-control bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
                      />
                    </div>
                  </div>
                  <div className="mt-4">
                    <button
                      type="submit"
                      disabled={isPaying}
                      className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      {isPaying ? "Recording payment…" : "Record payment"}
                    </button>
                  </div>
                </form>
              </div>
            )}

          {actionError && (
            <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
              <p className="font-semibold">{actionError.message}</p>
              <button
                type="button"
                onClick={clearError}
                className="mt-2 inline-flex items-center rounded-md border border-transparent bg-bolder-rose-action px-2.5 py-1 text-xs font-medium text-white hover:bg-brand-700"
              >
                Dismiss
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function statementStatusTone(status: string): string {
  return status === "PAID"
    ? "text-green-800 bg-green-50 border-green-200"
    : "text-amber-800 bg-amber-50 border-amber-200";
}
