import { describe, it, expect } from "vitest";
import {
  PRODUCT_ROUTE_IDS,
  createOverlayState,
  isOverlayState,
} from "@/features/customer/navigation";

describe("customer navigation", () => {
  it("marks an overlay with the current page load's token", () => {
    const state = createOverlayState();

    expect(state.productOverlay).toBe(true);
    expect(isOverlayState(state)).toBe(true);
  });

  it("does not treat an ordinary navigation as an overlay", () => {
    expect(isOverlayState(null)).toBe(false);
    expect(isOverlayState(undefined)).toBe(false);
    expect(isOverlayState({})).toBe(false);
    expect(isOverlayState("something else")).toBe(false);
    expect(isOverlayState({ productOverlay: false, overlayToken: "x" })).toBe(false);
  });

  it("does not treat a marker from another page load as an overlay", () => {
    // A reload evaluates the module again and issues a new token, so a
    // marker left over from the previous page load no longer matches. This
    // is the comparison the reload rule rests on.
    expect(isOverlayState({ productOverlay: true, overlayToken: "a-stale-token" })).toBe(false);
  });

  it("gives the two product routes distinct ids", () => {
    // React Router requires route ids to be globally unique within a data
    // router, so the two product routes cannot share one.
    expect(PRODUCT_ROUTE_IDS.storefront).toBe("storefront-product");
    expect(PRODUCT_ROUTE_IDS.search).toBe("search-product");
    expect(PRODUCT_ROUTE_IDS.storefront).not.toBe(PRODUCT_ROUTE_IDS.search);
  });
});
