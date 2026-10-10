import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  adjustStock,
  createProduct,
  deactivateProduct,
  deleteProductImage,
  fetchCategories,
  fetchInventory,
  fetchLowStockProducts,
  fetchProduct,
  fetchProductImages,
  fetchProducts,
  fetchStockMovements,
  reorderProductImages,
  setPrimaryProductImage,
  stockIn,
  stockOut,
  updateExpiryDate,
  updateLowStockThreshold,
  updateProduct,
  uploadProductImage,
  writeOffStock,
  fetchOwnProfile,
  updateOwnProfile,
} from "@/features/vendor/api";
import {
  INVENTORY_PAGE_SIZE,
  type CategoryPageResponse,
  type ExpiryDateRequest,
  type InventorySummary,
  type LowStockThresholdRequest,
  type Product,
  type ProductImage,
  type ProductListFilters,
  type ProductRequest,
  type StockActionRequest,
  type StockActionKind,
  type StockAdjustmentRequest,
  type StockInRequest,
  type StockOutRequest,
  type StockWriteOffRequest,
  type VendorProfile,
  type VendorProfileUpdateRequest,
} from "@/features/vendor/types";

export const vendorKeys = {
  all: ["vendor"] as const,
  profile: ["vendor", "profile"] as const,
  products: {
    all: ["vendor", "products"] as const,
    list: (filters: ProductListFilters) =>
      [
        "vendor",
        "products",
        "list",
        filters.status,
        filters.categoryId,
        filters.name,
        filters.page,
      ] as const,
    detail: (productId: number) => ["vendor", "products", "detail", productId] as const,
    images: (productId: number) => ["vendor", "products", "images", productId] as const,
    movements: (productId: number, page: number) =>
      ["vendor", "products", "movements", productId, page] as const,
  },
  inventory: {
    all: ["vendor", "inventory"] as const,
    detail: (productId: number) => ["vendor", "inventory", "detail", productId] as const,
    /** Prefix for every page of the low-stock list, so a stock change can drop them all. */
    lowStockRoot: ["vendor", "inventory", "low-stock"] as const,
    lowStock: (page: number) => ["vendor", "inventory", "low-stock", page] as const,
  },
};

/**
 * Categories are global reference data, not vendor data: they are served by a
 * `permitAll` endpoint and carry nothing about the caller. They therefore sit
 * outside the `["vendor"]` prefix, so `clearVendorCache` does not throw them away
 * on logout and the next vendor does not re-fetch a list that cannot have changed
 * for them.
 */
export const categoryKeys = {
  all: ["categories"] as const,
};

/**
 * The single vendor read. Every vendor screen (dashboard, profile, delivery
 * settings, operating hours) and the status banner in the layout share this one
 * query key, so the whole area costs a single `GET /api/v1/vendors/profile`.
 *
 * No polling is configured: in Phase 2 the only thing that changes this data is
 * the vendor's own save or an admin transition, and both are handled by
 * invalidating/writing the cache on the next navigation or mutation.
 */
export function useVendorProfile() {
  return useQuery({
    queryKey: vendorKeys.profile,
    queryFn: fetchOwnProfile,
  });
}

/**
 * The single vendor write. All four vendor screens save through the same PUT, so
 * invalidation happens in one place.
 */
export function useUpdateVendorProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: VendorProfileUpdateRequest) => updateOwnProfile(payload),
    onSuccess: (profile: VendorProfile) => {
      // The PUT response is the complete, server-normalised profile, so writing
      // it into the cache replaces the old value without a second round trip.
      queryClient.setQueryData(vendorKeys.profile, profile);
    },
  });
}

/**
 * Drops every cached vendor query, including the catalog and inventory keys that
 * now share the `["vendor"]` prefix. Called on logout so a different account
 * signing in on the same tab never sees the previous vendor's products, stock
 * levels or movement log before its own queries resolve.
 */
export function clearVendorCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: vendorKeys.all });
}

/* -------------------------------------------------------------------------- */
/* Catalog (plan task 3.9)                                                     */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/categories` — the shared reference list behind both category UIs. */
export function useCategories() {
  return useQuery<CategoryPageResponse>({
    queryKey: categoryKeys.all,
    queryFn: fetchCategories,
    // Reference data that an admin can change; a five-minute window keeps two
    // vendor tabs from disagreeing for long without a refetch storm.
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * `GET /api/v1/vendors/products`, keyed by every filter and the page.
 *
 * `placeholderData` is left at its default (no previous page retained), because a
 * page or filter change must show a loading state rather than the previous
 * filter's rows under new controls — the same reasoning as the admin listings.
 */
export function useProducts(filters: ProductListFilters) {
  return useQuery({
    queryKey: vendorKeys.products.list(filters),
    queryFn: ({ signal }) => fetchProducts(filters, signal),
  });
}

/** `GET /api/v1/vendors/products/{productId}`. */
export function useProduct(productId: number | null) {
  return useQuery({
    queryKey: vendorKeys.products.detail(productId ?? 0),
    queryFn: () => fetchProduct(productId as number),
    enabled: productId !== null,
  });
}

/** `GET /api/v1/vendors/products/{productId}/images`, in display order. */
export function useProductImages(productId: number | null) {
  return useQuery({
    queryKey: vendorKeys.products.images(productId ?? 0),
    queryFn: () => fetchProductImages(productId as number),
    enabled: productId !== null,
  });
}

/**
 * `POST /api/v1/vendors/products`.
 *
 * On success the listing is invalidated rather than patched: a new product can
 * change the total, land on a different page, or fall outside the active status or
 * category filter entirely, so the list has to be re-read to stay truthful.
 */
export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    // Wrapped rather than passed as `mutationFn: createProduct`: useMutation calls
    // the function with a second context argument, which would then be handed to the
    // API client as a trailing parameter of a body that must be exactly `ProductRequest`.
    mutationFn: (payload: ProductRequest) => createProduct(payload),
    onSuccess: (product: Product) => {
      queryClient.setQueryData(vendorKeys.products.detail(product.id), product);
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    },
  });
}

/**
 * `PUT /api/v1/vendors/products/{productId}`.
 *
 * The response is written into the detail cache (it carries the regenerated slug
 * and the new status) *and* the listing is invalidated: a rename or a status
 * change moves the row out of the active filter and off the current page.
 */
export function useUpdateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, ...payload }: { productId: number } & ProductRequest) =>
      updateProduct(productId, payload),
    onSuccess: (product: Product) => {
      queryClient.setQueryData(vendorKeys.products.detail(product.id), product);
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    },
  });
}

/** `PATCH /api/v1/vendors/products/{productId}/deactivate` (soft delete). */
export function useDeactivateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (productId: number) => deactivateProduct(productId),
    onSuccess: (_data, productId) => {
      // The detail row is dropped rather than refetched: the page navigates away
      // on success, and leaving a cached `ACTIVE` product behind would let the
      // back button show a product the server has already delisted.
      queryClient.removeQueries({ queryKey: vendorKeys.products.detail(productId) });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Product images (plan task 3.9)                                              */
/* -------------------------------------------------------------------------- */

/**
 * Every image mutation writes the server's returned ordered list straight into
 * the images cache.
 *
 * `setPrimary` and `reorder` return the whole list precisely because more than
 * one row changed (D-28), and `upload` invalidates instead: the response is one
 * image, and the sort order of the existing ones is not derivable from it. The
 * product detail is invalidated alongside, because `ProductResponse.images` is a
 * copy of this same list and would otherwise disagree with it.
 */
export function useUploadProductImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      productId,
      file,
      primary,
    }: {
      productId: number;
      file: File;
      primary: boolean;
    }) => uploadProductImage(productId, file, primary),
    onSuccess: (_image, { productId }) => {
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.images(productId) });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.detail(productId) });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    },
  });
}

export function useSetPrimaryProductImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, imageId }: { productId: number; imageId: number }) =>
      setPrimaryProductImage(productId, imageId),
    onSuccess: (images: ProductImage[], { productId }) => {
      queryClient.setQueryData(vendorKeys.products.images(productId), images);
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.detail(productId) });
    },
  });
}

export function useReorderProductImages() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, imageIds }: { productId: number; imageIds: number[] }) =>
      reorderProductImages(productId, imageIds),
    onSuccess: (images: ProductImage[], { productId }) => {
      queryClient.setQueryData(vendorKeys.products.images(productId), images);
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.detail(productId) });
    },
  });
}

/**
 * `DELETE /api/v1/vendors/products/{productId}/images/{imageId}`.
 *
 * Answers `204` with no body, so there is no ordered list to write — both the
 * images query and the product detail are re-read.
 */
export function useDeleteProductImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, imageId }: { productId: number; imageId: number }) =>
      deleteProductImage(productId, imageId),
    onSuccess: (_data, { productId }) => {
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.images(productId) });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.detail(productId) });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Inventory (plan task 3.9)                                                   */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/vendors/products/{productId}/inventory`. */
export function useInventory(productId: number | null) {
  return useQuery({
    queryKey: vendorKeys.inventory.detail(productId ?? 0),
    queryFn: () => fetchInventory(productId as number),
    enabled: productId !== null,
  });
}

/** `GET /api/v1/vendors/inventory/low-stock?page`. */
export function useLowStockProducts(page: number) {
  return useQuery({
    queryKey: vendorKeys.inventory.lowStock(page),
    queryFn: ({ signal }) => fetchLowStockProducts(page, INVENTORY_PAGE_SIZE, signal),
  });
}

/** `GET /api/v1/vendors/products/{productId}/inventory/movements?page`. */
export function useStockMovements(productId: number | null, page: number) {
  return useQuery({
    queryKey: vendorKeys.products.movements(productId ?? 0, page),
    queryFn: ({ signal }) => fetchStockMovements(productId as number, page, INVENTORY_PAGE_SIZE, signal),
    enabled: productId !== null,
  });
}

/**
 * Every inventory mutation, as one keyed mutation.
 *
 * `stock-in`, `stock-out`, `adjustment` and `write-off` differ only in route and
 * body shape, and each answers with the same complete `InventorySummary`. The
 * variables are the `StockActionRequest` union so "a write-off with no reason" is a
 * compile error rather than a 400 found in the browser, and so the reason-bearing
 * actions cannot be sent through the optional-reason branch.
 *
 * On success three things are reconciled, and all three are needed because a single
 * stock change can invalidate any of them:
 *  - the returned summary is written into the product's inventory cache and into
 *    the cached product's embedded copy, so the numbers on screen are the numbers
 *    the server committed rather than a re-fetch that could race;
 *  - the catalog listing is invalidated, because it renders stock per row;
 *  - the low-stock listing is invalidated, because crossing the threshold moves a
 *    product into or out of that list.
 * The movement history is deliberately *not* invalidated here: it is refetched by
 * its own page navigation and is append-only, so it never shows a stale row that
 * the server has since contradicted.
 */
export function useStockAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variables: { productId: number; action: StockActionKind } & StockActionRequest) => {
      const { productId, action, ...payload } = variables;

      switch (action) {
        case "stock-in":
          return stockIn(productId, payload as StockInRequest);
        case "stock-out":
          return stockOut(productId, payload as StockOutRequest);
        case "adjustment":
          return adjustStock(productId, payload as StockAdjustmentRequest);
        case "write-off":
          return writeOffStock(productId, payload as StockWriteOffRequest);
      }
    },
    onSuccess: (summary: InventorySummary, variables) => {
      const { productId } = variables;
      queryClient.setQueryData(vendorKeys.inventory.detail(productId), summary);
      queryClient.setQueryData<Product | undefined>(vendorKeys.products.detail(productId), (product) =>
        product ? { ...product, inventory: summary } : product,
      );
      void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
      void queryClient.invalidateQueries({ queryKey: vendorKeys.inventory.lowStockRoot });
    },
  });
}

/**
 * The two alert settings. Separate from `useStockAction` because they take no
 * movement and have no reason, but they invalidate exactly the same keys for the
 * same reason: they change what the low-stock list contains.
 */
export function useInventorySettings() {
  const queryClient = useQueryClient();

  const applySuccess = (summary: InventorySummary, productId: number) => {
    queryClient.setQueryData(vendorKeys.inventory.detail(productId), summary);
    queryClient.setQueryData<Product | undefined>(vendorKeys.products.detail(productId), (product) =>
      product ? { ...product, inventory: summary } : product,
    );
    void queryClient.invalidateQueries({ queryKey: vendorKeys.products.all });
    void queryClient.invalidateQueries({ queryKey: vendorKeys.inventory.lowStockRoot });
  };

  const threshold = useMutation({
    mutationFn: ({ productId, ...payload }: LowStockThresholdRequest & { productId: number }) =>
      updateLowStockThreshold(productId, payload),
    onSuccess: (summary, variables) => applySuccess(summary, variables.productId),
  });

  const expiryDate = useMutation({
    mutationFn: ({ productId, ...payload }: ExpiryDateRequest & { productId: number }) =>
      updateExpiryDate(productId, payload),
    onSuccess: (summary, variables) => applySuccess(summary, variables.productId),
  });

  return { threshold, expiryDate };
}