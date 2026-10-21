import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { useAddCartItem } from "@/features/cart/queries";
import { toApiError } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

interface AddToCartButtonProps {
  productId: number;
  productName: string;
  quantity?: number;
  /** The optional gift note, sent with the existing cart request. */
  note?: string | null;
  className?: string;
}

const BUTTON_CLASS =
  "rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50";

/**
 * Adds a product to the customer's cart.
 *
 * The rule about who may add lives here, in one place, because the cart
 * endpoint is `CUSTOMER`-only and the customer area is public:
 *
 * - **Anonymous visitor** — the button sends them to the login page with the
 *   page they were on in `location.state.from`, which is the same convention
 *   `ProtectedRoute` uses and the same one `LoginPage` already reads. The
 *   *destination* is preserved; the pending add is **not**. Replaying an add
 *   after an arbitrary amount of time would put an item in a cart the
 *   customer never confirmed, so the customer returns to the product and
 *   presses the button again.
 * - **Signed in, but not a customer** — the button is disabled and says why.
 *   The cart endpoint would answer 403, and stating the fact beats provoking
 *   a failed request.
 * - **Customer** — the existing `useAddCartItem` mutation posts to
 *   `POST /api/v1/cart/items` and invalidates the existing cart query key, so
 *   the header badge updates without the cart area being touched at all.
 */
export function AddToCartButton({
  productId,
  productName,
  quantity = 1,
  note = null,
  className = "",
}: AddToCartButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const addItem = useAddCartItem();
  const [added, setAdded] = useState(false);

  const isAnonymous = user === null;
  const isNotACustomer = user !== null && user.role !== "CUSTOMER";

  if (isAnonymous) {
    return (
      <button
        type="button"
        onClick={() => navigate("/login", { state: { from: location.pathname } })}
        aria-label={`Sign in to add ${productName} to your cart`}
        className={`${BUTTON_CLASS} ${FOCUS_RING} ${PRESSABLE} ${className}`}
      >
        Sign in to add to cart
      </button>
    );
  }

  if (isNotACustomer) {
    return (
      <button
        type="button"
        disabled
        aria-label={`Only customer accounts can add ${productName} to a cart`}
        title="Only customer accounts can add items to a cart."
        className={`${BUTTON_CLASS} ${className}`}
      >
        Sign in as a customer to add to cart
      </button>
    );
  }

  const error = addItem.error ? toApiError(addItem.error).message : null;

  return (
    <div className={className}>
      <button
        type="button"
        disabled={addItem.isPending}
        onClick={() => {
          setAdded(false);
          addItem.mutate(
            { productId, quantity, note },
            { onSuccess: () => setAdded(true) },
          );
        }}
        aria-label={`Add ${productName} to cart`}
        className={`${BUTTON_CLASS} ${FOCUS_RING} ${PRESSABLE}`}
      >
        {addItem.isPending ? "Adding..." : "Add to cart"}
      </button>

      {added && !addItem.isPending && (
        <p role="status" className="mt-2 text-sm text-green-700">
          {productName} added to your cart.
        </p>
      )}

      {error && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
