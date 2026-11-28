import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearVendorCache, useVendorProfile } from "@/features/vendor/queries";
import { PRESSABLE } from "@/motion/pressable";

interface Product {
  productId: number;
  name: string;
  basePrice: number;
  expiryDate?: string;
  status?: string;
  expired?: boolean;
  expiredReason?: string;
}

interface TicketItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  available?: number;
  expired?: boolean;
}

interface TicketState {
  items: TicketItem[];
  payment: "CASH" | "CARD" | "UPI";
  subtotal: number;
  total: number;
}

interface PrintReceiptData {
  transactionId: string;
  vendorName: string;
  createdAt: string;
  paymentMethod: string;
  lines: Array<{
    productId: number;
    productName: string;
    unitPrice: number;
    quantity: number;
    lineTotal: number;
  }>;
  total: number;
}

const CART_MAX_QUANTITY = 99;

export function VendorPosPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const { data: profile } = useVendorProfile();

  const handleLogout = () => {
    clearVendorCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  // POS state
  const [ticket, setTicket] = useState<TicketState>({
    items: [],
    payment: "CASH",
    subtotal: 0,
    total: 0,
  });
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPrint, setShowPrint] = useState(false);
  const [printReceipt, setPrintReceipt] = useState<PrintReceiptData | null>(null);
  const [printWindow, setPrintWindow] = useState<Window | null>(null);

  // Product grid state
  const [searchQuery, setSearchQuery] = useState("");
  const [showSidebar, setShowSidebar] = useState(false);

  // Error state
  const [error, setError] = useState<string | null>(null);
  const [isLoading] = useState(false);

  // Product data from API (simulated for initial view)
  const [products] = useState<Product[]>([]);

  // --- Product Search & Filter ---
  const filteredProducts = products.filter((p) => {
    const searchMatch =
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.name.toLowerCase().includes((searchQuery || "").toLowerCase().substring(0, 3));
    const notExpired = !p.expired || (p.expiredReason && searchQuery);
    return searchMatch && notExpired;
  });

  // --- Idempotency Key Management ---
  const generateIdempotencyKey = (): string => {
    return "pos-" + Math.random().toString(36).substring(2, 22);
  };

  const isKeyValid = () => {
    return (
      idempotencyKey != null &&
      /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(
        idempotencyKey
      )
    );
  };

  // --- Ticket Management ---
  const addItem = (productId: number, productName: string, price: number) => {
    const existing = ticket.items.find((i) => i.productId === productId);
    if (existing) {
      const newQty = Math.min(existing.quantity + 1, CART_MAX_QUANTITY);
      const newItems = ticket.items.map((i) =>
        i.productId === productId
          ? { ...i, quantity: newQty, lineTotal: Number((newQty * i.unitPrice).toFixed(2)) }
          : i
      );
      const newSubtotal = Number(newItems.reduce((sum, item) => sum + item.lineTotal, 0).toFixed(2));
      setTicket({
        ...ticket,
        items: newItems,
        subtotal: newSubtotal,
        total: newSubtotal,
      });
    } else {
      const newItems = [
        ...ticket.items,
        {
          productId,
          productName,
          quantity: 1,
          unitPrice: price,
          lineTotal: price,
        },
      ];
      const newSubtotal = Number(newItems.reduce((sum, item) => sum + item.lineTotal, 0).toFixed(2));
      setTicket({
        ...ticket,
        items: newItems,
        subtotal: newSubtotal,
        total: newSubtotal,
      });
    }
  };

  const removeItem = (productId: number) => {
    const newItems = ticket.items.filter((i) => i.productId !== productId);
    const newSubtotal = Number(newItems.reduce((sum, item) => sum + item.lineTotal, 0).toFixed(2));
    setTicket({
      ...ticket,
      items: newItems,
      subtotal: newSubtotal,
      total: newSubtotal,
    });
  };

  const setQuantity = (productId: number, quantity: number) => {
    const newQty = Math.min(Math.max(1, quantity), CART_MAX_QUANTITY);
    const newItems = ticket.items.map((i) =>
      i.productId === productId
        ? { ...i, quantity: newQty, lineTotal: Number((newQty * i.unitPrice).toFixed(2)) }
        : i
    );
    const newSubtotal = Number(newItems.reduce((sum, item) => sum + item.lineTotal, 0).toFixed(2));
    setTicket({
      ...ticket,
      items: newItems,
      subtotal: newSubtotal,
      total: newSubtotal,
    });
  };

  // --- Payment Selection ---
  const handlePaymentChange = (payment: "CASH" | "CARD" | "UPI") => {
    setTicket({ ...ticket, payment });
  };

  // --- Sale Submission ---
  const handleSale = async () => {
    if (!isSubmitting && ticket.items.length > 0 && isKeyValid()) {
      setIsSubmitting(true);
      setError(null);

      // Simulate API call - in production calls /api/v1/pos/sales
      setTimeout(() => {
        setIsSubmitting(false);
        const saleResponse: PrintReceiptData = {
          transactionId: "pos-" + Date.now(),
          vendorName: profile?.businessName || "Florist",
          createdAt: new Date().toISOString(),
          paymentMethod: ticket.payment,
          lines: ticket.items.map((item) => ({
            productId: item.productId,
            productName: item.productName,
            unitPrice: item.unitPrice,
            quantity: item.quantity,
            lineTotal: item.lineTotal,
          })),
          total: ticket.total,
        };

        setPrintReceipt(saleResponse);
        setShowPrint(true);

        // Reset ticket and generate new key
        setTicket({
          items: [],
          payment: "CASH",
          subtotal: 0,
          total: 0,
        });
        setIdempotencyKey(generateIdempotencyKey());
      }, 800);
    }
  };

  // --- Print Receipt ---
  const handlePrint = () => {
    if (printReceipt && !printWindow) {
      const printWindowRef = window.open("", "_blank");
      if (printWindowRef) {
        setPrintWindow(printWindowRef);
        printWindowRef.document.write(`
          <html>
            <head>
              <title>POS Receipt</title>
              <style>
                @media print {
                  body { font-family: sans-serif; margin: 0; padding: 0; }
                  .pos-receipt { width: 320px; padding: 10px; font-size: 12px; }
                  .header { border-bottom: 2px solid #000; padding-bottom: 10px; margin-bottom: 10px; }
                  .total { font-weight: bold; text-align: right; margin-top: 20px; }
                  .item { margin: 5px 0; }
                }
              </style>
            </head>
            <body>
              <div class="pos-receipt">
                <div class="header">
                  <strong>${printReceipt.vendorName}</strong>
                  <div>${printReceipt.createdAt}</div>
                </div>
                <div>Payment: ${printReceipt.paymentMethod}</div>
                <table>
                  ${printReceipt.lines.map(
                    (line) => `<tr><td>${line.productName}</td><td>${line.quantity}x ${line.unitPrice}</td><td>${line.lineTotal}</td></tr>`
                  ).join("")}
                </table>
                <div class="total">Total: $${printReceipt.total.toFixed(2)}</div>
                <button onclick="window.print()">Print Receipt</button>
                <button onclick="window.close()">Close</button>
              </div>
            </body>
          </html>`
        );
      }
    }
  };

  // --- Render ---
  const hasItems = ticket.items.length > 0;

  return (
    <div className="min-h-screen bg-bolder-bg">
      {/* Sidebar toggle button for mobile */}
      {showSidebar && (
        <button
          onClick={() => setShowSidebar(false)}
          className="fixed top-4 left-4 z-10 p-2 rounded-md bg-bolder-surface text-white"
        >
          ←
        </button>
      )}

      {/* Fullscreen POS overlay */}
      <div className="relative w-full max-w-full md:max-w-full bg-bolder-bg min-h-screen">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-bolder-line">
          <h1 className="text-2xl font-bold text-bolder-text">POS</h1>
          <div className="flex items-center gap-2">
            <span className="text-sm text-bolder-muted">FLORIST</span>
            <button
              onClick={handleLogout}
              className="text-sm text-bolder-muted hover:text-bolder-text"
            >
              Logout
            </button>
          </div>
        </div>

        {/* Search & Product Grid */}
        <div className="p-4">
          <div className="mb-4">
            <div className="relative">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search products..."
                className="w-full px-3 py-2 rounded-md border border-bolder-line-control shadow-sm focus:ring-bolder-rose focus:border-bolder-rose"
                disabled={isLoading}
              />
              {isLoading && (
                <span className="absolute right-2 text-bolder-muted text-xs">Loading...</span>
              )}
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery("")}
                  className="absolute right-2 top-2 text-bolder-muted text-sm select-none"
                  title="Clear search"
                >
                  ×
                </button>
              )}
            </div>
          </div>

          {/* Product Grid */}
          <div className="grid grid-cols-2 gap-3 px-1">
            {filteredProducts.map((product) => {
              const isExpired = product.expired && !searchQuery;
              const available = product.basePrice > 0 ? 999 : 0;
              const disabled = isExpired || product.basePrice <= 0;

              return (
                <div
                  key={product.productId}
                  className={`group rounded border border-bolder-line-control p-2 hover:bg-bolder-surface transition-colors ${
                    disabled ? "opacity-50 cursor-not-allowed" : ""
                  }`}
                >
                  <div className="h-24 flex items-center justify-center">
                    <span className="text-sm text-bolder-muted">
                      {product.name.split(" ")[0].charAt(0)}
                    </span>
                  </div>
                  <div className="pt-2 text-sm text-bolder-text">
                    <span className="font-medium">{product.name}</span>
                  </div>
                  <div className="text-right">
                    <span className="font-bold text-bolder-text">
                      ${product.basePrice.toFixed(2)}
                    </span>
                    <span className="text-xs text-bolder-muted">/{available} avail</span>
                  </div>
                  {isExpired && (
                    <span className="text-xs text-bolder-muted block mt-1">
                      {product.expiredReason || "Expired"}
                    </span>
                  )}
                  <button
                    onClick={() => addItem(product.productId, product.name, product.basePrice)}
                    disabled={disabled || isSubmitting}
                    className={`mt-1 w-full rounded border border-bolder-line-control px-2 py-2 text-sm text-bolder-text hover:bg-bolder-bg disabled:opacity-50 ${PRESSABLE}`}
                  >
                    Quick Add
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Running Ticket */}
        <div className="p-4 border-t border-bolder-line">
          <div className="flex justify-between items-center">
            <span className="text-sm text-bolder-muted">Ticket</span>
            <span className="text-sm font-medium text-bolder-text">
              ${ticket.subtotal.toFixed(2)}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 mt-2">
            {ticket.items.map((item) => (
              <div key={item.productId} className="flex items-center">
                <span className="text-xs text-bolder-muted">{item.productName}</span>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setQuantity(item.productId, item.quantity + 1)}
                    className={`${PRESSABLE} rounded border border-bolder-line-control px-1 py-0.5 text-xs`}
                  >
                    +
                  </button>
                  <input
                    type="number"
                    min={1}
                    max={CART_MAX_QUANTITY}
                    value={item.quantity}
                    onChange={(e) =>
                      setQuantity(item.productId, Number(e.target.value))
                    }
                    className="w-10 rounded border border-bolder-line-control px-1 text-center text-sm"
                  />
                  <button
                    onClick={() => removeItem(item.productId)}
                    className={`${PRESSABLE} rounded border border-bolder-line-control px-1 py-0.5 text-xs`}
                  >
                    −
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Total */}
          <div className="mt-3 pt-3 border-t border-bolder-line">
            <div className="flex justify-between text-sm text-bolder-muted">
              <span>Subtotal</span>
              <span>${ticket.subtotal.toFixed(2)}</span>
            </div>
            <div className="font-bold text-bolder-text">
              Total: ${ticket.total.toFixed(2)}
            </div>
          </div>
        </div>

        {/* Payment Selection */}
        <div className="p-4 border-t border-bolder-line">
          <div className="flex gap-2">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                checked={ticket.payment === "CASH"}
                onChange={() => handlePaymentChange("CASH")}
                className="w-4 h-4 rounded border-bolder-line-control focus:ring-bolder-rose"
              />
              <span className="text-bolder-muted">CASH</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                checked={ticket.payment === "CARD"}
                onChange={() => handlePaymentChange("CARD")}
                className="w-4 h-4 rounded border-bolder-line-control focus:ring-bolder-rose"
              />
              <span className="text-bolder-muted">CARD</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                checked={ticket.payment === "UPI"}
                onChange={() => handlePaymentChange("UPI")}
                className="w-4 h-4 rounded border-bolder-line-control focus:ring-bolder-rose"
              />
              <span className="text-bolder-muted">UPI</span>
            </label>
          </div>
        </div>

        {/* Charge Button */}
        <div className="p-4 border-t border-bolder-line">
          <button
            onClick={handleSale}
            disabled={isSubmitting || !isKeyValid() || !hasItems}
            className={`w-full rounded border border-bolder-line-control px-4 py-2 text-lg font-medium text-bolder-text hover:bg-bolder-bg disabled:opacity-50 ${PRESSABLE}`}
          >
            {isSubmitting ? "Processing..." : "Charge"}
          </button>
        </div>

        {/* Error State */}
        {error && (
          <div className="p-4 rounded border border-bolder-danger-border bg-bolder-danger-surface text-bolder-danger mt-2">
            <p>{error}</p>
          </div>
        )}

        {/* Print Receipt State */}
        {showPrint && printReceipt && (
          <div className="fixed inset-0 bg-bolder-overlay backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="rounded-md bg-bolder-surface p-6 max-w-sm w-full">
              <h2 className="text-xl font-bold text-bolder-text mb-4">Receipt</h2>
              <pre className="text-xs text-bolder-muted whitespace-pre">
                {JSON.stringify(printReceipt, null, 2)}
              </pre>
              <div className="mt-4 flex justify-end gap-2">
                <button
                  onClick={() => {
                    setShowPrint(false);
                    setPrintWindow(null);
                  }}
                  className="rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-muted"
                >
                  Close
                </button>
                <button
                  onClick={handlePrint}
                  className="rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-text"
                >
                  Print
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Sidebar (mobile) - hidden on larger screens */}
        {showSidebar && (
          <div className="fixed inset-0 bg-bolder-overlay backdrop-blur-sm z-40">
            <div className="h-full w-64 bg-bolder-surface p-4 flex flex-col">
              <h2 className="text-xl font-bold text-bolder-text mb-4">POS</h2>
              <button
                onClick={() => setShowSidebar(false)}
                className="mt-4 rounded border border-bolder-line-control px-2 py-1 text-sm text-bolder-muted"
              >
                Close
              </button>
              <nav className="mt-4 space-y-2">
                <a href="/vendor" className="block rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-muted">
                  Dashboard
                </a>
                <a href="#" className="block rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-muted">
                  Catalog
                </a>
                <a href="#" className="block rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-muted">
                  Inventory
                </a>
                <a href="#" className="block rounded border border-bolder-line-control px-3 py-1 text-sm text-bolder-muted">
                  Orders
                </a>
              </nav>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}