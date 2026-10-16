/**
 * Cart page (plan task 5.6).
 *
 * Lists the customer's own cart grouped by vendor. Each line shows the product,
 * the snapshotted price, a quantity control and a remove button. Per-vendor
 * sections show the shop subtotal and whether every line in that shop is still
 * purchasable. A grand total and a "Proceed to checkout" button close the page.
 *
 * Price drift is shown per item (`priceChanged`): the snapshotted price is the
 * cart's price until checkout re-validates it, so the current price sits next to
 * it as information, not as a recalculation.
 *
 * Non-purchasable items stay visible — the snapshot and quantity are still
 * useful information — but the checkout button is disabled while any line in any
 * shop is not purchasable.
 */

import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart, useUpdateCartItem, useDeleteCartItem } from "@/features/cart/queries";
import type { CartItem, CartShopView } from "@/features/cart/types";
import { CART_MAX_QUANTITY } from "@/features/cart/types";
import { formatMoney } from "@/features/vendor/format";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { FlowerLoader } from "@/motion/FlowerLoader";

/** Mirrors the `plusQuantity` / `minusQuantity` labels used across the item rows. */
const QUANTITY_STEP = 1;

export function CartPage() {
  const navigate = useNavigate();
  const { data: cart, isPending, error, refetch } = useCart();
  const updateItem = useUpdateCartItem();
  const deleteItem = useDeleteCartItem();

  const [adjusting, setAdjusting] = useState<{ [itemId: number]: boolean }>({});

  if (error) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  if (isPending || !cart) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Loading your cart" showLabel className="text-sm text-slate-600" />
      </div>
    );
  }

  if (cart.items.length === 0) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <PageHeading
          title="Your cart is empty"
          description="Browse the storefront and add flowers to your cart — they'll show up here, grouped by shop."
        />
        <Link
          to="/browse"
          className={`mt-6 inline-flex items-center rounded-md border border-bolder-blush bg-white px-5 py-2.5 text-sm font-medium text-bolder-blush hover:bg-amber-50 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Start browsing
        </Link>
      </div>
    );
  }

  const shopsByVendor = new Map<number, CartShopView>();
  cart.shops.forEach((shop) => shopsByVendor.set(shop.vendorId, shop));

  const itemsByVendor = new Map<number, CartItem[]>();
  cart.items.forEach((item) => {
    const list = itemsByVendor.get(item.vendorId) ?? [];
    list.push(item);
    itemsByVendor.set(item.vendorId, list);
  });

  const allPurchasable = cart.items.every((item) => item.purchasable);

  return (
    <div className="space-y-6">
      <PageHeading
        title="Your cart"
        description={`${cart.totalItems} item${cart.totalItems === 1 ? "" : "s"} across ${cart.shops.length} shop${cart.shops.length === 1 ? "" : "s"}.`}
      />

      {Array.from(shopsByVendor.entries()).map(([vendorId, shop]) => (
        <CartShopSection
          key={vendorId}
          vendorId={vendorId}
          shop={shop}
          items={itemsByVendor.get(vendorId) ?? []}
          adjusting={adjusting}
          setAdjusting={setAdjusting}
          onQuantityChange={(itemId, quantity) => {
            void updateItem
              .mutateAsync({ itemId, payload: { quantity } })
              .finally(() => setAdjusting((prev) => ({ ...prev, [itemId]: false })));
          }}
          onRemove={(itemId) => {
            void deleteItem
              .mutateAsync(itemId)
              .finally(() => setAdjusting((prev) => ({ ...prev, [itemId]: false })));
          }}
        />
      ))}

      <CartSummary
        subtotal={cart.subtotal}
        allPurchasable={allPurchasable}
        onCheckout={() => navigate("/checkout")}
      />
    </div>
  );
}

interface CartShopSectionProps {
  vendorId: number;
  shop: CartShopView;
  items: CartItem[];
  adjusting: { [itemId: number]: boolean };
  setAdjusting: (next: (prev: { [itemId: number]: boolean }) => { [itemId: number]: boolean }) => void;
  onQuantityChange: (itemId: number, quantity: number) => void;
  onRemove: (itemId: number) => void;
}

function CartShopSection({ shop, items, adjusting, setAdjusting, onQuantityChange, onRemove }: CartShopSectionProps) {
  const purchasable = shop.purchasable;

  return (
    <Card title={shop.vendorName}>
      <div className="mt-4 space-y-3">
        {items.map((item) => (
          <CartItem
            key={item.id}
            item={item}
            shopPurchasable={purchasable}
            isAdjusting={adjusting[item.id] ?? false}
            onAdjustingChange={(adjusting) => setAdjusting((prev) => ({ ...prev, [item.id]: adjusting }))}
            onQuantityChange={onQuantityChange}
            onRemove={onRemove}
          />
        ))}
        <div className="flex justify-between items-baseline pt-3 border-t border-glass-border-soft">
          <span className="text-base font-semibold text-slate-900">Shop subtotal</span>
          <span className="text-lg font-bold text-slate-900">{formatMoney(shop.subtotal)}</span>
        </div>
      </div>
    </Card>
  );
}

interface CartItemProps {
  item: CartItem;
  shopPurchasable: boolean;
  isAdjusting: boolean;
  onAdjustingChange: (adjusting: boolean) => void;
  onQuantityChange: (itemId: number, quantity: number) => void;
  onRemove: (itemId: number) => void;
}

function CartItem({ item, shopPurchasable, isAdjusting, onAdjustingChange, onQuantityChange, onRemove }: CartItemProps) {
  const priceChanged = item.priceChanged;
  const purchasable = item.purchasable && shopPurchasable;
  const disabled = !purchasable || isAdjusting;

  return (
    <div className="flex items-center gap-4 border-b border-glass-border-soft pb-3 last:border-0 last:pb-0">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-900 truncate">{item.productName}</p>
        <p className="text-xs text-slate-500 truncate">{item.productSlug}</p>
        {priceChanged && (
          <p className="mt-1 text-xs font-medium text-amber-800">
            Price changed since you added this: {formatMoney(item.currentPrice)} now
          </p>
        )}
        {!purchasable && (
          <p className="mt-1 text-xs font-medium text-red-700">{item.notPurchasableReason ?? "Not available right now."}</p>
        )}
      </div>

      <div className="flex items-center gap-3 shrink-0">
        <span className="text-sm font-medium text-slate-700 w-16 text-right">{formatMoney(item.priceSnapshot)}</span>

        <div className="flex items-center gap-1.5 border border-glass-border rounded-sm">
          <button
            type="button"
            disabled={item.quantity <= 1 || disabled}
            onClick={() => {
              onAdjustingChange(true);
              onQuantityChange(item.id, item.quantity - QUANTITY_STEP);
              onAdjustingChange(false);
            }}
            className={`rounded-l-sm p-1.5 text-slate-600 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING} ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
            aria-label={`Reduce ${item.productName} quantity`}
          >
            −
          </button>
          <span className="text-sm font-medium text-slate-900 min-w-[2rem] text-center">{item.quantity}</span>
          <button
            type="button"
            disabled={item.quantity >= CART_MAX_QUANTITY || disabled}
            onClick={() => {
              onAdjustingChange(true);
              onQuantityChange(item.id, item.quantity + QUANTITY_STEP);
              onAdjustingChange(false);
            }}
            className={`rounded-r-sm p-1.5 text-slate-600 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING} ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
            aria-label={`Increase ${item.productName} quantity`}
          >
            +
          </button>
        </div>

        <button
          type="button"
          disabled={disabled}
          onClick={() => {
            onAdjustingChange(true);
            onRemove(item.id);
            onAdjustingChange(false);
          }}
          className={`text-xs font-medium text-red-700 hover:bg-red-50 ${PRESSABLE} ${FOCUS_RING} ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
          aria-label={`Remove ${item.productName} from cart`}
        >
          Remove
        </button>
      </div>
    </div>
  );
}

interface CartSummaryProps {
  subtotal: number;
  allPurchasable: boolean;
  onCheckout: () => void;
}

function CartSummary({ subtotal, allPurchasable, onCheckout }: CartSummaryProps) {
  return (
    <Card>
      <div className="flex justify-between items-baseline mb-4 border-b border-glass-border-soft pb-3">
        <span className="text-base font-semibold text-slate-900">Cart total</span>
        <span className="text-xl font-bold text-slate-900">{formatMoney(subtotal)}</span>
      </div>

      <button
        type="button"
        disabled={!allPurchasable}
        onClick={onCheckout}
        className={`w-full rounded-md bg-brand-600 px-5 py-3 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
      >
        Proceed to checkout
      </button>

      {!allPurchasable && (
        <p className="mt-3 text-xs text-amber-800">
          Some items in your cart are not available right now. Update or remove them before checking out.
        </p>
      )}
    </Card>
  );
}

// Exported constant so tests can reference the same bound the service enforces.
export { CART_MAX_QUANTITY };
