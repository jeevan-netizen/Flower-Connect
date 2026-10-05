import api from "@/shared/lib/api";
import type {
  CategoryPageResponse,
  ExpiryDateRequest,
  InventorySummary,
  LowStockPageResponse,
  LowStockThresholdRequest,
  Product,
  ProductImage,
  ProductListFilters,
  ProductPageResponse,
  ProductRequest,
  StockAdjustmentRequest,
  StockInRequest,
  StockMovementPageResponse,
  StockOutRequest,
  StockWriteOffRequest,
  VendorProfile,
  VendorProfileUpdateRequest,
} from "@/features/vendor/types";

/**
 * `GET /api/v1/vendors/profile` — the authenticated vendor's own profile.
 *
 * There is no vendor id in the path: the backend resolves the profile from the
 * JWT subject, so a vendor cannot read another vendor's row (plan task 2.5).
 * Requires `ROLE_FLORIST`.
 */
export async function fetchOwnProfile(): Promise<VendorProfile> {
  const response = await api.get<VendorProfile>("/vendors/profile");
  return response.data;
}

/**
 * `PUT /api/v1/vendors/profile` — full replacement of the vendor's own profile
 * and delivery settings. `hours` is a sub-resource: send it to replace the whole
 * week, omit it to leave the stored week untouched.
 */
export async function updateOwnProfile(
  payload: VendorProfileUpdateRequest,
): Promise<VendorProfile> {
  const response = await api.put<VendorProfile>("/vendors/profile", payload);
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Categories (plan task 3.1)                                                  */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/categories` — the active category reference list.
 *
 * `permitAll`, like `GET /api/v1/locations`: categories are shared reference
 * data rather than vendor-private data, and both the product form's category
 * picker and the catalog's category filter read from this one request.
 */
export async function fetchCategories(): Promise<CategoryPageResponse> {
  const response = await api.get<CategoryPageResponse>("/categories");
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Catalog (plan tasks 3.5, 3.8)                                               */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/vendors/products` — one page of the authenticated vendor's own
 * products, newest first.
 *
 * Absent filters are omitted from the params rather than sent as `null`, so an
 * unfiltered listing is byte-identical to one the backend serves with no query
 * string at all. `name=""` is dropped for the same reason: `ProductSpecifications
 * .nameContains` treats a blank term as no filter.
 */
export async function fetchProducts(
  filters: ProductListFilters,
  signal?: AbortSignal,
): Promise<ProductPageResponse> {
  const params: Record<string, string | number> = { page: filters.page };

  if (filters.status) {
    params.status = filters.status;
  }
  if (filters.categoryId !== null) {
    params.categoryId = filters.categoryId;
  }
  const name = filters.name.trim();
  if (name) {
    params.name = name;
  }

  const response = await api.get<ProductPageResponse>("/vendors/products", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendors/products/{productId}` — one of the vendor's products. */
export async function fetchProduct(productId: number): Promise<Product> {
  const response = await api.get<Product>(`/vendors/products/${productId}`);
  return response.data;
}

/** `POST /api/v1/vendors/products` — creates the product and its inventory row. */
export async function createProduct(payload: ProductRequest): Promise<Product> {
  const response = await api.post<Product>("/vendors/products", payload);
  return response.data;
}

/**
 * `PUT /api/v1/vendors/products/{productId}`.
 *
 * A changed name regenerates the slug server-side, so the response's `slug` is
 * the authoritative one and is written into the cache rather than patched.
 */
export async function updateProduct(productId: number, payload: ProductRequest): Promise<Product> {
  const response = await api.put<Product>(`/vendors/products/${productId}`, payload);
  return response.data;
}

/**
 * `PATCH /api/v1/vendors/products/{productId}/deactivate` — the soft delete.
 * Nothing is physically removed, so the historical movement log stays readable.
 */
export async function deactivateProduct(productId: number): Promise<void> {
  await api.patch<void>(`/vendors/products/${productId}/deactivate`);
}

/* -------------------------------------------------------------------------- */
/* Product images (plan task 3.8)                                              */
/* -------------------------------------------------------------------------- */

/**
 * `POST /api/v1/vendors/products/{productId}/images` — multipart upload.
 *
 * The `Content-Type` override is load-bearing, not decoration. The shared Axios
 * instance sets `Content-Type: application/json` by default, and its
 * `transformRequest` reads that header: with a JSON content type it does **not**
 * pass `FormData` through, it JSON-stringifies it, so the part would arrive as
 * `{"file":{},...}` instead of a multipart body. Declaring `multipart/form-data`
 * keeps the body as `FormData`; axios then drops that header again because it
 * carries no boundary, and the browser sets it with the right one.
 *
 * Format is decided server-side from the bytes (D-27), so no client-side type
 * check is sent and the filename is only for display.
 */
export async function uploadProductImage(
  productId: number,
  file: File,
  primary = false,
): Promise<ProductImage> {
  const form = new FormData();
  form.append("file", file);
  form.append("primary", String(primary));

  const response = await api.post<ProductImage>(
    `/vendors/products/${productId}/images`,
    form,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}

/** `GET /api/v1/vendors/products/{productId}/images` — in display order. */
export async function fetchProductImages(productId: number): Promise<ProductImage[]> {
  const response = await api.get<ProductImage[]>(`/vendors/products/${productId}/images`);
  return response.data;
}

/**
 * `PUT /api/v1/vendors/products/{productId}/images/{imageId}/primary`.
 *
 * Returns the whole ordered list rather than one image because two rows changed
 * (D-28), which is what lets the caller replace its list without re-reading.
 */
export async function setPrimaryProductImage(
  productId: number,
  imageId: number,
): Promise<ProductImage[]> {
  const response = await api.put<ProductImage[]>(
    `/vendors/products/${productId}/images/${imageId}/primary`,
  );
  return response.data;
}

/**
 * `PUT /api/v1/vendors/products/{productId}/images/order`.
 *
 * `imageIds` must be an exact permutation of the product's images; a partial or
 * repeated list is refused server-side rather than guessed at, so the caller
 * always sends the full ordered set.
 */
export async function reorderProductImages(
  productId: number,
  imageIds: number[],
): Promise<ProductImage[]> {
  const response = await api.put<ProductImage[]>(
    `/vendors/products/${productId}/images/order`,
    { imageIds },
  );
  return response.data;
}

/**
 * `DELETE /api/v1/vendors/products/{productId}/images/{imageId}` — removes the row
 * and its stored object, promoting the next image when the cover is deleted.
 */
export async function deleteProductImage(productId: number, imageId: number): Promise<void> {
  await api.delete<void>(`/vendors/products/${productId}/images/${imageId}`);
}

/* -------------------------------------------------------------------------- */
/* Inventory (plan task 3.6)                                                   */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/vendors/products/{productId}/inventory`. */
export async function fetchInventory(productId: number): Promise<InventorySummary> {
  const response = await api.get<InventorySummary>(`/vendors/products/${productId}/inventory`);
  return response.data;
}

/**
 * `GET /api/v1/vendors/inventory/low-stock` — the shop-wide list of products at
 * or below their own threshold.
 */
export async function fetchLowStockProducts(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<LowStockPageResponse> {
  const response = await api.get<LowStockPageResponse>("/vendors/inventory/low-stock", {
    params: { page, size },
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/products/{productId}/inventory/movements` — the
 * append-only movement history, newest first.
 */
export async function fetchStockMovements(
  productId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<StockMovementPageResponse> {
  const response = await api.get<StockMovementPageResponse>(
    `/vendors/products/${productId}/inventory/movements`,
    { params: { page, size }, signal },
  );
  return response.data;
}

/** `POST /api/v1/vendors/products/{productId}/inventory/stock-in`. */
export async function stockIn(
  productId: number,
  payload: StockInRequest,
): Promise<InventorySummary> {
  const response = await api.post<InventorySummary>(
    `/vendors/products/${productId}/inventory/stock-in`,
    payload,
  );
  return response.data;
}

/** `POST /api/v1/vendors/products/{productId}/inventory/stock-out`. */
export async function stockOut(
  productId: number,
  payload: StockOutRequest,
): Promise<InventorySummary> {
  const response = await api.post<InventorySummary>(
    `/vendors/products/${productId}/inventory/stock-out`,
    payload,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/products/{productId}/inventory/adjustments` — a signed
 * correction with a mandatory reason.
 */
export async function adjustStock(
  productId: number,
  payload: StockAdjustmentRequest,
): Promise<InventorySummary> {
  const response = await api.post<InventorySummary>(
    `/vendors/products/${productId}/inventory/adjustments`,
    payload,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/products/{productId}/inventory/write-offs` — a `WASTE`
 * movement with a mandatory reason.
 */
export async function writeOffStock(
  productId: number,
  payload: StockWriteOffRequest,
): Promise<InventorySummary> {
  const response = await api.post<InventorySummary>(
    `/vendors/products/${productId}/inventory/write-offs`,
    payload,
  );
  return response.data;
}

/**
 * `PUT /api/v1/vendors/products/{productId}/inventory/low-stock-threshold`.
 *
 * Writes no movement, but takes the same row lock as a stock change because
 * Hibernate's whole-row `UPDATE` would otherwise write back a stale quantity
 * (D-24).
 */
export async function updateLowStockThreshold(
  productId: number,
  payload: LowStockThresholdRequest,
): Promise<InventorySummary> {
  const response = await api.put<InventorySummary>(
    `/vendors/products/${productId}/inventory/low-stock-threshold`,
    payload,
  );
  return response.data;
}

/**
 * `PUT /api/v1/vendors/products/{productId}/inventory/expiry-date`. Sends `null`
 * to clear the stored date.
 */
export async function updateExpiryDate(
  productId: number,
  payload: ExpiryDateRequest,
): Promise<InventorySummary> {
  const response = await api.put<InventorySummary>(
    `/vendors/products/${productId}/inventory/expiry-date`,
    payload,
  );
  return response.data;
}
