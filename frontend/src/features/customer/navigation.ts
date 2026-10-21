import { useCallback, useMemo } from "react";
import { useLocation, useMatches, useNavigate } from "react-router-dom";

/**
 * The route ids of the two product-detail routes.
 *
 * They must be **distinct**: React Router requires route ids to be globally
 * unique within a data router, and `createBrowserRouter` throws
 * "Found a route id collision" when two routes share one. The id is also how a
 * surface recognises that its product child is the matched route, which is why
 * it is set explicitly rather than inferred from the URL.
 */
export const PRODUCT_ROUTE_IDS = {
  storefront: "storefront-product",
  search: "search-product",
} as const;

/** The navigation state that marks a product as an overlay of its surface. */
export interface ProductOverlayState {
  productOverlay: true;
  /**
   * Identifies the JavaScript page load that wrote the marker.
   *
   * React Router keeps navigation state in the history entry, and the browser
   * preserves that state across a same-document reload — so a marker alone
   * cannot tell "the customer opened this from the shop" from "the customer
   * reloaded this page". The token does: a reload evaluates this module again
   * and issues a new token, so a marker left over from the previous page load
   * no longer matches and the product renders on its own.
   */
  overlayToken: string;
}

/** What the surface tells the product page about how it is being shown. */
export interface ProductSurfaceContext {
  /** True when the product is an overlay with the surface still behind it. */
  isOverlay: boolean;
  /** Returns to the surface. */
  close: () => void;
  /** Where the surface lives, for the standalone product's return link. */
  returnPath: string;
  /** What the return link says, e.g. "the shop" or "the results". */
  returnLabel: string;
}

/**
 * A token for the current JavaScript page load.
 *
 * It is generated once, when this module is first evaluated, and lives in
 * memory only: it is never persisted, never written to storage and never sent
 * to the server. It is not a secret — it only has to differ between page
 * loads.
 *
 * A development-only consequence: a Vite hot reload re-evaluates this module
 * and therefore issues a new token, so an overlay open at that moment would
 * fall back to the standalone presentation until the customer navigates again.
 */
const OVERLAY_TOKEN = createOverlayToken();

function createOverlayToken(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * The marker a product link carries, so the surface can tell an overlay
 * apart from a direct entry.
 *
 * Only product links carry it: React Router writes navigation state only when
 * a link supplies it, so no other route in the app can be mistaken for an
 * overlay.
 */
export function createOverlayState(): ProductOverlayState {
  return { productOverlay: true, overlayToken: OVERLAY_TOKEN };
}

/**
 * Whether the given navigation state marks an overlay of the current page
 * load.
 *
 * A missing, malformed or stale marker is not an overlay — which is what
 * makes a direct entry and a reload both render the product on its own.
 */
export function isOverlayState(state: unknown): state is ProductOverlayState {
  if (typeof state !== "object" || state === null) {
    return false;
  }
  const candidate = state as Partial<ProductOverlayState>;
  return candidate.productOverlay === true && candidate.overlayToken === OVERLAY_TOKEN;
}

/** What a surface knows about the product route it may be showing. */
export interface ProductSurface {
  /** True when a product-detail child route is matched. */
  productChildMatched: boolean;
  /** True when the matched product is an overlay of this surface. */
  isOverlay: boolean;
  /** Returns to the surface, from an overlay. */
  close: () => void;
  /** Where the surface lives, for the standalone product's return link. */
  returnPath: string;
  /** What the return link says. */
  returnLabel: string;
}

/**
 * How the surface hosting a product route is presenting it.
 *
 * The rule is explicit rather than inferred from the navigation type: a
 * product is an overlay when the URL carries a marker written by the current
 * page load, and it is a standalone page otherwise. A direct entry has no
 * marker, and a reload has a marker whose token no longer matches.
 *
 * `close` pops the in-app predecessor when the product is an overlay. The
 * predecessor exists by construction: the marker is written only by a product
 * link, and that link pushes an entry, so the entry before it is the surface
 * the customer was on. A standalone product never calls it — it offers a
 * visible return link instead.
 */
export function useProductOverlay(
  childRouteId: string,
  surfacePath: string,
  surfaceLabel: string,
): ProductSurface {
  const matches = useMatches();
  const location = useLocation();
  const navigate = useNavigate();

  const productChildMatched = matches.some((match) => match.id === childRouteId);
  const isOverlay = productChildMatched && isOverlayState(location.state);

  const close = useCallback(() => {
    if (isOverlay) {
      navigate(-1);
      return;
    }
    navigate(surfacePath);
  }, [isOverlay, navigate, surfacePath]);

  return useMemo(
    () => ({
      productChildMatched,
      isOverlay,
      close,
      returnPath: surfacePath,
      returnLabel: surfaceLabel,
    }),
    [productChildMatched, isOverlay, close, surfacePath, surfaceLabel],
  );
}
