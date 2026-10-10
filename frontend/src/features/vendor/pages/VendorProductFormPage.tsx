import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import type {
  ProductRequest,
  StockActionKind,
  StockActionRequest,
} from "@/features/vendor/types";
import {
  useCategories,
  useCreateProduct,
  useDeactivateProduct,
  useDeleteProductImage,
  useInventory,
  useInventorySettings,
  useProduct,
  useProductImages,
  useReorderProductImages,
  useSetPrimaryProductImage,
  useStockAction,
  useStockMovements,
  useUpdateProduct,
  useUploadProductImage,
} from "@/features/vendor/queries";
import { ProductForm } from "@/features/vendor/components/ProductForm";
import { ProductImageManager } from "@/features/vendor/components/ProductImageManager";
import { InventoryPanel } from "@/features/vendor/components/InventoryPanel";
import { MovementHistoryTable } from "@/features/vendor/components/MovementHistoryTable";
import {
  StockActionDialog,
  explainStockFailure,
} from "@/features/vendor/components/StockActionDialog";
import { CatalogLoader } from "@/features/vendor/components/ApprovedVendorGate";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import { FormErrorSummary, SuccessMessage } from "@/features/vendor/components/FormFields";
import { toApiError } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

/**
 * The product editor (plan task 3.9: "product create form", "product edit form",
 * image upload, reorder and cover selection, "inventory table with stock
 * adjustments", "movement history", "low-stock badges").
 *
 * One screen serves both create and edit. They are the same form against the same
 * DTO — `ProductRequest` is the body of both verbs — and splitting them would
 * duplicate every field, bound and message for the sake of a heading that differs
 * by one word. The media and stock sections only appear once there is a product id,
 * because every route they need is keyed by one.
 *
 * After a create the page navigates to the new product's own URL rather than
 * staying put. That is what a merchant expects from "Add product": the thing they
 * just made is now the thing on screen, with its images and stock available
 * immediately, and the URL is shareable and refreshable.
 *
 * Every write here reports the server's own message. `INSUFFICIENT_STOCK` is
 * expanded with the reserved-units rule (D-24), a `VENDOR_NOT_APPROVED` refusal is
 * the layout's approval state rather than a generic failure (D-13), and a
 * `VALIDATION_FAILED` body lands on the matching input rather than on a page-level
 * banner.
 */
export function VendorProductFormPage() {
  const { productId: productParam } = useParams();
  const navigate = useNavigate();

  /*
   * `catalog/new` and `catalog/:productId` are two sibling routes, so the create
   * route has **no** `productId` param at all — `productParam` is `undefined`
   * there, not the string "new". That is what identifies the create screen.
   *
   * A param that *is* present must then be a positive integer. A non-numeric one is
   * a bad URL rather than a missing product, and falling back to "not valid" beats
   * asking the server about `NaN` — and beats silently showing an empty create form
   * at an address that claims to be editing something.
   */
  const parsedId = Number(productParam);
  const isCreating = productParam === undefined;
  const productId = isCreating ? null : parsedId;
  const idIsValid = isCreating || (Number.isInteger(parsedId) && parsedId > 0);

  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [stockAction, setStockAction] = useState<StockActionKind | null>(null);
  const [stockError, setStockError] = useState<string | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);
  const [confirmingDeactivate, setConfirmingDeactivate] = useState(false);
  const [movementPage, setMovementPage] = useState(0);

  const categories = useCategories();
  const product = useProduct(idIsValid && !isCreating ? productId : null);
  const images = useProductImages(idIsValid && !isCreating ? productId : null);
  const inventory = useInventory(idIsValid && !isCreating ? productId : null);
  const movements = useStockMovements(idIsValid && !isCreating ? productId : null, movementPage);

  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deactivate = useDeactivateProduct();
  const uploadImage = useUploadProductImage();
  const setPrimaryImage = useSetPrimaryProductImage();
  const reorderImages = useReorderProductImages();
  const deleteImage = useDeleteProductImage();
  const stockActionMutation = useStockAction();
  const { threshold, expiryDate } = useInventorySettings();

  if (!idIsValid) {
    return (
      <div className="space-y-4">
        <PageHeading title="Product" />
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          That product link is not valid. Open a product from your catalog instead.
        </p>
        <Link
          to="/vendor/catalog"
          className={`text-sm font-medium text-brand-700 hover:text-brand-900 ${FOCUS_RING}`}
        >
          Back to catalog
        </Link>
      </div>
    );
  }

  if (!isCreating && product.isPending) {
    return <CatalogLoader label="Loading this product..." />;
  }

  if (!isCreating && product.error) {
    return <VendorErrorState error={product.error} onRetry={() => void product.refetch()} />;
  }

  const current = product.data ?? null;
  const isSaving = createProduct.isPending || updateProduct.isPending;

  const submitProduct = async (payload: ProductRequest) => {
    setFormError(null);
    setSuccess(null);

    if (isCreating) {
      const created = await createProduct.mutateAsync(payload);
      setSuccess("Product created.");
      navigate(`/vendor/catalog/${created.id}`, { replace: true });
      return;
    }

    await updateProduct.mutateAsync({ productId: productId as number, ...payload });
    setSuccess("Product saved.");
  };

  const runStockAction = async (payload: StockActionRequest) => {
    setStockError(null);
    try {
      await stockActionMutation.mutateAsync({
        productId: productId as number,
        action: stockAction as StockActionKind,
        ...payload,
      });
      setStockAction(null);
      setMovementPage(0);
      setSuccess("Stock updated and recorded in the movement history.");
    } catch (caught) {
      const info = toApiError(caught);
      // Left open with the message shown: the vendor's typed values are still in the
      // dialog, so they can correct the number rather than re-enter it.
      setStockError(explainStockFailure(info, stockAction as StockActionKind));
      throw caught;
    }
  };

  const confirmDeactivate = async () => {
    setFormError(null);
    try {
      await deactivate.mutateAsync(productId as number);
      navigate("/vendor/catalog", { replace: true });
    } catch (caught) {
      setFormError(toApiError(caught).message);
    }
  };

  const categoryOptions = categories.data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title={isCreating ? "Add product" : `Edit ${current?.name ?? "product"}`}
        description={
          isCreating
            ? "Create the product first, then add its images and stock from the same screen."
            : "Product details, images, stock and movement history for this product."
        }
      />

      <Card>
        {/*
          A failed category read is reported inline rather than replacing the form:
          the vendor can still fix a name or a price, and a form with an empty
          category list would silently look like "no categories exist".
        */}
        {categories.error && (
          <p role="alert" className="mb-4 rounded-md bg-amber-50 p-3 text-sm text-amber-900">
            Categories could not be loaded, so a category cannot be chosen and the product cannot be
            saved.
          </p>
        )}

        <FormErrorSummary message={formError} />
        <SuccessMessage message={success} />

        <ProductForm
          product={current}
          categories={categoryOptions}
          isSubmitting={isSaving}
          onSubmit={submitProduct}
          onError={(info) => setFormError(info.message)}
        />
      </Card>

      {!isCreating && current && (
        <>
          <Card title="Images">
            <ProductImageManager
              images={images.data ?? []}
              isUploading={uploadImage.isPending}
              isMutating={
                setPrimaryImage.isPending || reorderImages.isPending || deleteImage.isPending
              }
              errorMessage={imageError}
              onUpload={async (file, primary) => {
                setImageError(null);
                try {
                  await uploadImage.mutateAsync({ productId: current.id, file, primary });
                } catch (caught) {
                  setImageError(toApiError(caught).message);
                  throw caught;
                }
              }}
              onSetPrimary={async (imageId) => {
                setImageError(null);
                try {
                  await setPrimaryImage.mutateAsync({ productId: current.id, imageId });
                } catch (caught) {
                  setImageError(toApiError(caught).message);
                  throw caught;
                }
              }}
              onMove={async (imageIds) => {
                setImageError(null);
                try {
                  await reorderImages.mutateAsync({ productId: current.id, imageIds });
                } catch (caught) {
                  setImageError(toApiError(caught).message);
                  throw caught;
                }
              }}
              onDelete={async (imageId) => {
                setImageError(null);
                try {
                  await deleteImage.mutateAsync({ productId: current.id, imageId });
                } catch (caught) {
                  setImageError(toApiError(caught).message);
                  throw caught;
                }
              }}
              onError={setImageError}
            />
          </Card>

          <Card title="Inventory">
            <InventoryPanel
              inventory={inventory.data ?? null}
              isPending={inventory.isPending}
              isSettingThreshold={threshold.isPending}
              isSettingExpiryDate={expiryDate.isPending}
              onAction={(action) => {
                setStockError(null);
                setStockAction(action);
              }}
              onSaveThreshold={(value) => {
                setFormError(null);
                threshold
                  .mutateAsync({ productId: current.id, lowStockThreshold: value })
                  .catch((caught) => setFormError(toApiError(caught).message));
              }}
              onSaveExpiryDate={(value) => {
                setFormError(null);
                expiryDate
                  .mutateAsync({ productId: current.id, expiryDate: value })
                  .catch((caught) => setFormError(toApiError(caught).message));
              }}
            />
          </Card>

          <Card title="Movement history">
            <MovementHistoryTable
              movements={movements.data}
              isPending={movements.isPending}
              onPageChange={setMovementPage}
              disabled={stockActionMutation.isPending}
            />
          </Card>

          <Card title="Remove product">
            <p className="text-sm text-slate-600">
              Deactivating hides the product from customers while keeping the row, its images and its
              full movement history. You can bring it back from the catalog at any time.
            </p>

            <AnimatePresence initial={false}>
              {confirmingDeactivate ? (
                <div
                  role="dialog"
                  aria-modal="true"
                  aria-label="Deactivate this product"
                  className="mt-3 rounded-md border border-red-200 bg-red-50 p-3"
                >
                  <p className="text-sm text-red-900">
                    Deactivate &ldquo;{current.name}&rdquo;? It will stop appearing in customer search.
                  </p>
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      disabled={deactivate.isPending}
                      onClick={() => void confirmDeactivate()}
                      className={`rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      {deactivate.isPending ? "Deactivating..." : "Yes, deactivate"}
                    </button>
                    <button
                      type="button"
                      disabled={deactivate.isPending}
                      onClick={() => setConfirmingDeactivate(false)}
                      className={`rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmingDeactivate(true)}
                  className={`mt-3 rounded-md border border-red-300 bg-white px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50 ${PRESSABLE} ${FOCUS_RING}`}
                >
                  Deactivate product
                </button>
              )}
            </AnimatePresence>
          </Card>
        </>
      )}

      <AnimatePresence>
        {stockAction && current && (
          <StockActionDialog
            key={`${current.id}-${stockAction}`}
            action={stockAction}
            productName={current.name}
            inventory={inventory.data ?? null}
            isSubmitting={stockActionMutation.isPending}
            errorMessage={stockError}
            onConfirm={runStockAction}
            onCancel={() => {
              setStockError(null);
              setStockAction(null);
            }}
          />
        )}
      </AnimatePresence>
    </div>
  );
}