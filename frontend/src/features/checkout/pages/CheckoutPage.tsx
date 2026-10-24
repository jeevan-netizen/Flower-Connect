/**
 * Checkout page (plan task 5.6).
 *
 * A four-step wizard: choose a saved delivery address, pick a delivery slot
 * per vendor, review the quote (including any price-drift confirmation), then
 * place the order. Each step gates on the previous one — the slot step needs the
 * address, the review step needs a slot per vendor, and the confirmation step
 * needs a successful quote.
 *
 * The checkout endpoint is idempotent: a `clientOrderId` UUID is generated once
 * per attempt and preserved across retries, so a network hiccup never creates
 * duplicate orders. Price drift is confirmed explicitly — if the quote has
 * changed since the cart was built, the customer must opt in before the order
 * is placed.
 */

import { useState, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useCart, clearCartCache } from "@/features/cart/queries";
import {
  useAddresses,
  useSlots,
  useCheckoutQuote,
  usePlaceCheckout,
} from "@/features/checkout/queries";
import type {
  AddressResponse,
  CheckoutQuoteResponse,
  CheckoutResponse,
  CheckoutStep,
} from "@/features/checkout/types";
import {
  PAYMENT_METHODS,
  type PaymentMethod,
} from "@/features/payment/types";
import { formatMoney } from "@/features/vendor/format";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { FOCUS_RING, FIELD_TRANSITION, PRESSABLE } from "@/motion/pressable";
import { FlowerLoader } from "@/motion/FlowerLoader";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

/**
 * Browser-local date as `YYYY-MM-DD`.
 * Used for the date picker's `min` attribute (UI convenience, not authoritative validation).
 */
export function getBrowserLocalDate(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // All hooks must come before any conditional return, so hook order is stable
  // across renders regardless of loading/error/empty state.
  const { data: cart, isPending: cartPending, error: cartError, refetch: refetchCart } = useCart();
  const { data: addressesPage, isPending: addressesPending, error: addressesError, refetch: refetchAddresses } =
    useAddresses(0, 50);
  const quote = useCheckoutQuote();
  const placeCheckout = usePlaceCheckout();

  const [step, setStep] = useState<CheckoutStep>("address");
  const [addressId, setAddressId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("COD");
  const [deliveryDate, setDeliveryDate] = useState(getBrowserLocalDate());
  const [slotSelections, setSlotSelections] = useState<Record<number, string>>({});
  const [quoteData, setQuoteData] = useState<CheckoutQuoteResponse | null>(null);
  const [quoteError, setQuoteError] = useState<unknown>(null);
  const quoteRequestIdRef = useRef(0);
  const [confirmPriceChanges, setConfirmPriceChanges] = useState(false);
  const [orderResult, setOrderResult] = useState<CheckoutResponse | null>(null);
  const [placing, setPlacing] = useState(false);
  const placingRef = useRef(false);
  const [placementError, setPlacementError] = useState<ApiErrorInfo | null>(null);

  /** Stable idempotency key for the current checkout attempt. */
  const [clientOrderId] = useState(() => crypto.randomUUID());

  // Derived values — safe for a null cart (guarded below) so they compute on
  // every render and keep hook order stable.
  const vendors = cart?.items
    ? Array.from(new Map(cart.items.map((item) => [item.vendorId, item.vendorName]))).map(
        ([vendorId, vendorName]) => ({ vendorId, vendorName }),
      )
    : [];
  const allSlotsSelected = vendors.every((vendor) => Boolean(slotSelections[vendor.vendorId]));

  const handleQuote = useCallback(async () => {
    if (quote.isPending) return;
    if (!addressId || !allSlotsSelected) return;

    const requestId = ++quoteRequestIdRef.current;
    setQuoteError(null);

    const items: { vendorId: number; slotStart: string }[] = vendors.map((vendor) => ({
      vendorId: vendor.vendorId,
      slotStart: slotSelections[vendor.vendorId]!,
    }));

    try {
      const result = await quote.mutateAsync({ addressId, items });
      if (quoteRequestIdRef.current !== requestId) return;
      setQuoteData(result);
      setStep("review");
    } catch (error) {
      if (quoteRequestIdRef.current !== requestId) return;
      setQuoteData(null);
      setQuoteError(error);
    }
  }, [addressId, allSlotsSelected, vendors, slotSelections, quote]);

  /** Picking a new address, date, or slot invalidates the quote and clears stale quote errors. */
  const onAddressChange = useCallback((id: number | null) => {
    quoteRequestIdRef.current += 1;
    setAddressId(id);
    setQuoteData(null);
    setQuoteError(null);
    if (!quote.isPending) {
      quote.reset();
    }
  }, [quote]);

  const onPaymentMethodChange = useCallback((method: PaymentMethod) => {
    setPaymentMethod(method);
  }, []);

  const onSlotChange = useCallback((vendorId: number, slotStart: string) => {
    quoteRequestIdRef.current += 1;
    setSlotSelections((prev) => ({ ...prev, [vendorId]: slotStart }));
    setQuoteData(null);
    setQuoteError(null);
    if (!quote.isPending) {
      quote.reset();
    }
  }, [quote]);

  const onDateChange = useCallback((date: string) => {
    quoteRequestIdRef.current += 1;
    setDeliveryDate(date);
    setQuoteData(null);
    setQuoteError(null);
    if (!quote.isPending) {
      quote.reset();
    }
  }, [quote]);

  const handlePlaceOrder = useCallback(async () => {
    if (placingRef.current) return;
    if (!quoteData || !addressId) return;

    placingRef.current = true;
    setPlacing(true);
    setPlacementError(null);

    try {
      const result = await placeCheckout.mutateAsync({
        addressId,
        items: quoteData.shops.map((shop) => ({
          vendorId: shop.vendorId,
          slotStart: slotSelections[shop.vendorId]!,
        })),
        clientOrderId,
        quoteHash: quoteData.quoteHash,
        confirmPriceChanges,
        paymentMethod,
      });
      setOrderResult(result);
      clearCartCache(queryClient);
      setStep("confirmation");
    } catch (error) {
      setPlacementError(toApiError(error));
    } finally {
      placingRef.current = false;
      setPlacing(false);
    }
  }, [addressId, quoteData, slotSelections, clientOrderId, confirmPriceChanges, paymentMethod, placeCheckout, queryClient]);

  if (cartError || addressesError) {
    return (
      <VendorErrorState
        error={cartError ?? addressesError}
        onRetry={() => {
          void refetchCart();
          void refetchAddresses();
        }}
      />
    );
  }

  if (cartPending || addressesPending || !cart) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <FlowerLoader label="Preparing checkout" showLabel className="text-sm text-slate-600" />
      </div>
    );
  }

  if (cart.items.length === 0) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <PageHeading title="Your cart is empty" description="Add some flowers before checking out." />
        <LinkToBrowse />
      </div>
    );
  }

  const stepTitle: Record<CheckoutStep, string> = {
    address: "Delivery address",
    slots: "Delivery slots",
    review: "Review order",
    confirmation: "Order placed",
  };

  const stepSubtitle: Record<CheckoutStep, string> = {
    address: "Choose where your flowers should be delivered.",
    slots: "Pick a delivery window for each shop.",
    review: "Confirm the prices and totals before we place the order.",
    confirmation: "Your orders are on their way.",
  };

  const allPurchasable = quoteData?.shops.every((shop) =>
    shop.items.every((item) => item.purchasable),
  ) ?? true;

  return (
    <div className="space-y-6">
      <PageHeading title={stepTitle[step]} description={stepSubtitle[step]} />

      <CheckoutStepper step={step} />

      {step === "address" && (
        <AddressStep
          addresses={addressesPage?.content ?? []}
          isPending={addressesPending}
          error={addressesError}
          selectedAddressId={addressId}
          paymentMethod={paymentMethod}
          onAddressChange={onAddressChange}
          onPaymentMethodChange={onPaymentMethodChange}
          onRetry={() => void refetchAddresses()}
          canProceed={Boolean(addressId)}
          onProceed={() => setStep("slots")}
        />
      )}

      {step === "slots" && (
        <SlotStep
          vendors={vendors}
          addressId={addressId ?? 0}
          deliveryDate={deliveryDate}
          slotSelections={slotSelections}
          onSlotChange={onSlotChange}
          onDateChange={onDateChange}
          allSlotsSelected={allSlotsSelected}
          onProceed={handleQuote}
          isPending={quote.isPending}
          error={quoteError}
          onRetry={handleQuote}
        />
      )}

      {step === "review" && (
        <ReviewStep
          quote={quoteData}
          isPending={quote.isPending}
          error={quoteError}
          placementError={placementError}
          allPurchasable={allPurchasable}
          confirmPriceChanges={confirmPriceChanges}
          onConfirmPriceChange={setConfirmPriceChanges}
          onRetry={handleQuote}
          onBack={() => setStep("slots")}
          onProceed={handlePlaceOrder}
          isPlacing={placing}
          canProceed={
            !quoteData?.hasErrors &&
            allPurchasable &&
            (!quoteData?.hasPriceChanges || confirmPriceChanges)
          }
        />
      )}

      {step === "confirmation" && (
        <ConfirmationStep
          result={orderResult}
          paymentMethod={paymentMethod}
          onDone={() => {
            if (paymentMethod === "ONLINE" && orderResult?.paymentGroupId) {
              navigate(`/payment/${orderResult.paymentGroupId}`);
            } else {
              navigate("/orders");
            }
          }}
        />
      )}
    </div>
  );
}

function LinkToBrowse() {
  return (
    <a
      href="/browse"
      className={`inline-flex items-center rounded-md border border-bolder-blush bg-white px-5 py-2.5 text-sm font-medium text-bolder-blush hover:bg-amber-50 ${PRESSABLE} ${FOCUS_RING}`}
    >
      Start browsing
    </a>
  );
}

/* -------------------------------------------------------------------------- */
/* Step: delivery address                                                     */
/* -------------------------------------------------------------------------- */

interface AddressStepProps {
  addresses: AddressResponse[];
  isPending: boolean;
  error: unknown;
  selectedAddressId: number | null;
  paymentMethod: PaymentMethod;
  onAddressChange: (id: number | null) => void;
  onPaymentMethodChange: (method: PaymentMethod) => void;
  onRetry: () => void;
  canProceed: boolean;
  onProceed: () => void;
}

function AddressStep({
  addresses,
  isPending,
  error,
  selectedAddressId,
  paymentMethod,
  onAddressChange,
  onPaymentMethodChange,
  onRetry,
  canProceed,
  onProceed,
}: AddressStepProps) {
  if (error) {
    return <VendorErrorState error={error} onRetry={onRetry} />;
  }

  if (isPending) {
    return (
      <Card>
        <FlowerLoader label="Loading your addresses" showLabel className="text-sm text-slate-600" />
      </Card>
    );
  }

  if (addresses.length === 0) {
    return (
      <Card>
        <p className="text-sm text-slate-600">You have no saved addresses yet.</p>
        <p className="mt-2 text-xs text-slate-500">
          Save an address in your account, then return here to check out.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <fieldset className="border-0 p-0 m-0">
        <legend className="text-sm font-semibold text-slate-900 mb-4">Delivery address</legend>
        <div className="space-y-3">
          {addresses.map((address) => (
            <label key={address.id} className="block">
              <input
                type="radio"
                name="address"
                checked={selectedAddressId === address.id}
                onChange={() => onAddressChange(address.id)}
                className={`${PRESSABLE} ${FOCUS_RING}`}
              />
              <div className="ml-3">
                <p className="text-sm font-medium text-slate-900">{address.label}</p>
                <p className="text-xs text-slate-500">
                  {address.line1} {address.line2 ?? ""} — {address.area}, {address.city} {address.pincode}
                </p>
                {address.defaultAddress && <span className="text-[10px] font-medium text-brand-700 uppercase tracking-tighter">Default</span>}
              </div>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="border-0 p-0 m-0 mt-6">
        <legend className="text-sm font-semibold text-slate-900 mb-4">Payment method</legend>
        <div className="space-y-2">
          {PAYMENT_METHODS.map((method) => (
            <label key={method} className="block cursor-pointer">
              <input
                type="radio"
                name="paymentMethod"
                checked={paymentMethod === method}
                onChange={() => onPaymentMethodChange(method)}
                className={`${PRESSABLE} ${FOCUS_RING}`}
              />
              <div className="ml-3">
                <p className="text-sm font-medium text-slate-900">
                  {method === "COD" ? "Cash on delivery" : "Online payment"}
                </p>
                <p className="text-xs text-slate-500">
                  {method === "COD"
                    ? "Pay the florist in cash when your flowers are delivered."
                    : "Pay online now; your payment is confirmed through a secure test-mode provider."}
                </p>
              </div>
            </label>
          ))}
        </div>
      </fieldset>

      <div className="mt-6 flex justify-end">
        <button
          type="button"
          disabled={!canProceed}
          onClick={onProceed}
          className={`rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
        >
          Choose delivery slots
        </button>
      </div>
    </Card>
  );
}

/* -------------------------------------------------------------------------- */
/* Step: delivery slots                                                       */
/* -------------------------------------------------------------------------- */

interface SlotStepProps {
  vendors: { vendorId: number; vendorName: string }[];
  addressId: number;
  deliveryDate: string;
  slotSelections: Record<number, string>;
  onSlotChange: (vendorId: number, slotStart: string) => void;
  onDateChange: (date: string) => void;
  allSlotsSelected: boolean;
  onProceed: () => void;
  isPending?: boolean;
  error?: unknown;
  onRetry?: () => void;
}

/** One vendor's slot picker. Calls `useSlots` directly so hook order is stable per instance. */
function SlotRow({
  vendorId,
  vendorName,
  addressId,
  deliveryDate,
  slotSelections,
  onSlotChange,
}: {
  vendorId: number;
  vendorName: string;
  addressId: number;
  deliveryDate: string;
  slotSelections: Record<number, string>;
  onSlotChange: (vendorId: number, slotStart: string) => void;
}) {
  const { data: slots, isPending: slotsPending } = useSlots(
    vendorId,
    addressId,
    deliveryDate,
  );
  const availableSlots = (slots ?? []).filter((s) => s.available);
  const selected = slotSelections[vendorId];

  return (
    <fieldset key={vendorId} className="border border-glass-border rounded-lg p-4 m-0">
      <legend className="text-sm font-semibold text-slate-900">{vendorName}</legend>
      {slotsPending && (
        <p className="mt-2 text-xs text-slate-500">Loading slots for {deliveryDate}...</p>
      )}
      {!slotsPending && availableSlots.length === 0 && (
        <p className="mt-2 text-xs text-red-700">No available slots on {deliveryDate} for this shop.</p>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        {availableSlots.map((slot) => {
          const isSelected = selected === slot.slotStart;
          return (
            <label
              key={`${vendorId}-${slot.slotStart}`}
              className="relative cursor-pointer"
            >
              <input
                type="radio"
                name={`slot-${vendorId}`}
                value={slot.slotStart}
                checked={isSelected}
                onChange={() => onSlotChange(vendorId, slot.slotStart)}
                className="peer sr-only"
              />
              <span
                className={`inline-block px-3 py-1.5 text-xs font-medium rounded-md border transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500 peer-focus-visible:ring-offset-2 ${
                  isSelected
                    ? "bg-brand-600 border-brand-600 text-white"
                    : "border-glass-border bg-white text-slate-700 hover:bg-slate-50"
                }`}
              >
                {formatSlotTime(slot.slotStart)} — {formatSlotTime(slot.slotEnd)}
              </span>
            </label>
          );
        })}
      </div>
      {!selected && !slotsPending && availableSlots.length > 0 && (
        <p className="mt-2 text-xs text-slate-500">Select a delivery window.</p>
      )}
    </fieldset>
  );
}

function SlotStep({
  vendors,
  addressId,
  deliveryDate,
  slotSelections,
  onSlotChange,
  onDateChange,
  allSlotsSelected,
  onProceed,
  isPending = false,
  error,
  onRetry,
}: SlotStepProps) {
  return (
    <Card>
      <fieldset className="border-0 p-0 m-0">
        <legend className="text-sm font-semibold text-slate-900 mb-4">Delivery date</legend>
        <div className="flex items-center gap-3 mb-6">
          <label htmlFor="delivery-date" className="text-sm text-slate-600">Deliver on</label>
          <input
            id="delivery-date"
            type="date"
            value={deliveryDate}
            onChange={(e) => onDateChange(e.target.value)}
            min={getBrowserLocalDate()}
            className={`rounded-sm border border-glass-border bg-white px-3 py-1.5 text-sm text-slate-900 focus:border-brand-500 focus:ring-brand-500 ${FIELD_TRANSITION}`}
          />
        </div>
      </fieldset>

      <div className="space-y-4">
        {vendors.map((vendor) => (
          <SlotRow
            key={vendor.vendorId}
            vendorId={vendor.vendorId}
            vendorName={vendor.vendorName}
            addressId={addressId}
            deliveryDate={deliveryDate}
            slotSelections={slotSelections}
            onSlotChange={onSlotChange}
          />
        ))}
      </div>

      {error ? (
        <div className="mt-6">
          <VendorErrorState error={error} onRetry={onRetry} />
        </div>
      ) : null}

      <div className="mt-6 flex justify-end">
        <button
          type="button"
          disabled={!allSlotsSelected || isPending}
          onClick={onProceed}
          className={`rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
        >
          {isPending ? "Preparing quote..." : "Review order"}
        </button>
      </div>
    </Card>
  );
}

function formatSlotTime(iso: string): string {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!match) return iso;
  const [, , hours, minutes] = match;
  const hour = Number(hours);
  const suffix = hour >= 12 ? "PM" : "AM";
  const displayHour = hour % 12 || 12;
  return `${displayHour}:${minutes} ${suffix}`;
}

/* -------------------------------------------------------------------------- */
/* Step: review order                                                         */
/* -------------------------------------------------------------------------- */

interface ReviewStepProps {
  quote: CheckoutQuoteResponse | null;
  isPending: boolean;
  error: unknown;
  placementError: ApiErrorInfo | null;
  allPurchasable: boolean;
  confirmPriceChanges: boolean;
  onConfirmPriceChange: (value: boolean) => void;
  onRetry: () => void;
  onBack: () => void;
  onProceed: () => void;
  isPlacing: boolean;
  canProceed: boolean;
}

function ReviewStep({
  quote,
  isPending,
  error,
  placementError,
  allPurchasable,
  confirmPriceChanges,
  onConfirmPriceChange,
  onRetry,
  onBack,
  onProceed,
  isPlacing,
  canProceed,
}: ReviewStepProps) {
  if (error) {
    return <VendorErrorState error={error} onRetry={onRetry} />;
  }

  if (placementError) {
    return <VendorErrorState error={placementError} onRetry={onRetry} />;
  }

  if (isPending || !quote) {
    return (
      <Card>
        <FlowerLoader label="Preparing your quote" showLabel className="text-sm text-slate-600" />
      </Card>
    );
  }

  return (
    <Card>
      <div className="space-y-4">
        {quote.shops.map((shop) => (
          <div key={shop.vendorId} className="border border-glass-border rounded-lg p-4">
            <div className="flex justify-between items-baseline mb-3">
              <p className="text-sm font-semibold text-slate-900">{shop.vendorName}</p>
              <span className="text-sm font-bold text-slate-900">{formatMoney(shop.total)}</span>
            </div>

            <div className="space-y-2 text-sm">
              {shop.items.map((item) => (
                <div key={item.productId} className="flex justify-between items-start">
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-slate-900 truncate">{item.productName}</p>
                    <p className="text-xs text-slate-500 truncate">Qty {item.quantity} • {formatMoney(item.priceSnapshot)} each</p>
                    {item.priceChanged && (
                      <p className="mt-1 text-xs font-medium text-amber-800">
                        Price changed: {formatMoney(item.currentPrice)} now
                      </p>
                    )}
                  </div>
                  <span className="text-slate-900">{formatMoney(item.subtotal)}</span>
                </div>
              ))}
              {!shop.minOrderMet && (
                <p className="text-xs font-medium text-red-700">
                  Below the {shop.vendorName} minimum order of {formatMoney(shop.minOrderAmount)}.
                </p>
              )}
              {!shop.withinRadius && (
                <p className="text-xs font-medium text-red-700">
                  Delivery address is outside {shop.vendorName}'s delivery radius.
                </p>
              )}
              {!shop.slotAvailable && (
                <p className="text-xs font-medium text-red-700">
                  The selected slot is no longer available for {shop.vendorName}.
                </p>
              )}
            </div>

            {shop.warnings.length > 0 && (
              <div className="mt-2 rounded-md bg-amber-50 p-2 text-xs text-amber-800">
                {shop.warnings.map((warning, i) => (
                  <p key={i} className="leading-relaxed">{warning}</p>
                ))}
              </div>
            )}
            {shop.errors.length > 0 && (
              <div className="mt-2 rounded-md bg-red-50 p-2 text-xs text-red-800">
                {shop.errors.map((error, i) => (
                  <p key={i} className="leading-relaxed">{error}</p>
                ))}
              </div>
            )}
          </div>
        ))}

        <div className="flex justify-between items-baseline pt-4 border-t border-glass-border-soft">
          <span className="text-base font-semibold text-slate-900">Order total</span>
          <span className="text-xl font-bold text-slate-900">{formatMoney(quote.total)}</span>
        </div>

        {quote.hasPriceChanges && (
          <div className="rounded-md border border-amber-200 bg-amber-50 p-3">
            <p className="text-sm font-semibold text-amber-900">Some prices have changed</p>
            <p className="mt-1 text-xs text-amber-800">
              One or more items cost more now than when you added them to your cart. Confirm the updated
              prices to continue.
            </p>
            <label className="mt-3 flex items-start gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={confirmPriceChanges}
                onChange={(e) => onConfirmPriceChange(e.target.checked)}
                className={`${PRESSABLE} ${FOCUS_RING} mt-0.5`}
              />
              <span className="text-sm text-amber-800 leading-none">
                I confirm the updated prices and totals.
              </span>
            </label>
          </div>
        )}

        {!allPurchasable && (
          <div className="rounded-md border border-red-200 bg-red-50 p-3">
            <p className="text-sm font-semibold text-red-900">Some items are no longer available</p>
            <p className="mt-1 text-xs text-red-800">
              Update your cart before placing this order.
            </p>
          </div>
        )}

        <div className="mt-6 flex justify-between">
          <button
            type="button"
            onClick={onBack}
            className={`rounded-md border border-glass-border bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Change slots
          </button>
          <button
            type="button"
            disabled={!canProceed || isPlacing}
            onClick={onProceed}
            className={`rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
          >
            {isPlacing ? "Placing order…" : "Place order"}
          </button>
        </div>
      </div>
    </Card>
  );
}

/* -------------------------------------------------------------------------- */
/* Step: confirmation                                                         */
/* -------------------------------------------------------------------------- */

interface ConfirmationStepProps {
  result: CheckoutResponse | null;
  paymentMethod: "COD" | "ONLINE";
  onDone: () => void;
}

function ConfirmationStep({
  result,
  paymentMethod,
  onDone,
}: ConfirmationStepProps) {
  if (!result) {
    return (
      <Card>
        <FlowerLoader label="Placing your order" showLabel className="text-sm text-slate-600" />
      </Card>
    );
  }

  return (
    <Card>
      <div className="text-center">
        <p className="text-2xl font-bold text-slate-900">Order placed</p>
        <p className="mt-2 text-sm text-slate-600">
          {result.idempotentReplay
            ? "This order was already placed — here are your existing orders."
            : "Your orders have been placed and stock has been reserved."}
        </p>

        <div className="mt-6 space-y-4">
          {result.orders.map((order) => (
            <div key={order.orderId} className="text-left">
              <p className="text-sm font-semibold text-slate-900">{order.vendorName}</p>
              <p className="text-xs text-slate-500">
                Order #{order.orderId} • {order.status} • {order.paymentMethod} • Slot {formatSlotTime(order.slotStart)}
              </p>
              <p className="text-sm text-slate-900">{formatMoney(order.total)}</p>
            </div>
          ))}
        </div>

        <div className="flex justify-between items-baseline pt-4 border-t border-glass-border-soft">
          <span className="text-base font-semibold text-slate-900">Total</span>
          <span className="text-xl font-bold text-slate-900">{formatMoney(result.total)}</span>
        </div>

        <div className="mt-6 flex justify-end">
          <button
            type="button"
            onClick={onDone}
            className={`rounded-md bg-brand-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
          >
            {paymentMethod === "ONLINE" ? "Track payment" : "View your orders"}
          </button>
        </div>
      </div>
    </Card>
  );
}

/** Minimal stepper showing progress through the checkout wizard. */
function CheckoutStepper({ step }: { step: CheckoutStep }) {
  const steps: CheckoutStep[] = ["address", "slots", "review", "confirmation"];
  const current = steps.indexOf(step);

  return (
    <nav aria-label="Checkout progress">
      <ol className="flex items-center gap-1 text-xs list-none p-0 m-0">
        {steps.map((name, i) => (
          <li
            key={name}
            aria-current={i === current ? "step" : undefined}
            className="flex items-center gap-2"
          >
            <div
              className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium transition-colors ${i <= current
                ? "bg-brand-600 text-white"
                : "border border-glass-border bg-white text-slate-400"
              }`}
            >
              {i + 1}
            </div>
            {i < steps.length - 1 && (
              <div
                aria-hidden="true"
                className={`h-0.5 w-4 ${i < current ? "bg-brand-600" : "bg-slate-200"}`}
              />
            )}
            <span className={`font-medium ${i <= current ? "text-slate-900" : "text-slate-400"}`}>
              {name === "address" ? "Address" : name === "slots" ? "Slots" : name === "review" ? "Review" : "Done"}
            </span>
          </li>
        ))}
      </ol>
    </nav>
  );
}
