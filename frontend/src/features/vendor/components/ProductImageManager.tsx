import { useState } from "react";
import { MAX_IMAGES_PER_PRODUCT, type ProductImage } from "@/features/vendor/types";
import { formatFileSize } from "@/features/vendor/format";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { StatusPill } from "@/motion/StatusPill";

const ACTION_BUTTON_CLASS = `rounded-md border bolder-line-control bg-bolder-surface px-2 py-1 text-xs font-medium text-bolder-text hover:bg-bolder-bg disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`;

interface ProductImageManagerProps {
  images: ProductImage[];
  isUploading: boolean;
  isMutating: boolean;
  /** Normalised backend failure from the last attempted image write, if any. */
  errorMessage: string | null;
  onUpload: (file: File, primary: boolean) => Promise<void>;
  onSetPrimary: (imageId: number) => Promise<void>;
  onMove: (imageIds: number[]) => Promise<void>;
  onDelete: (imageId: number) => Promise<void>;
  onError: (message: string | null) => void;
}

/**
 * Media section of the product editor (plan task 3.8 / 3.9).
 *
 * Three rules come straight from the backend rather than from UI preference:
 *
 * 1. **Reorder sends the whole list.** `ProductImageOrderRequest` must be an exact
 *    permutation of the product's image ids (D-28); a partial list cannot say
 *    where an omitted image belongs, so the move buttons recompute the full order
 *    and send all of it. That is also why there is no drag handle here — a
 *    reorder UI that could only ever produce a partial list would be a lie about
 *    what the endpoint accepts.
 * 2. **The budget is visible before the upload.** `max-images-per-product` is 8
 *    and the server enforces it under the same row lock as the one-primary rule,
 *    so two concurrent uploads cannot both slip past it. The input is disabled at
 *    the limit as a courtesy; the 409 is still handled if it arrives.
 * 3. **Cover selection is a real toggle, and the first image is free.** The server
 *    makes the first image of a product its cover automatically, and promotes the
 *    next one when the cover is deleted, so this section never has to offer a
 *    "set a cover" step to get a product usable.
 *
 * **Thumbnails are not rendered.** `ProductImageResponse.storageKey` is the opaque
 * backend key and the catalog API deliberately does not serve the bytes — there is
 * no image route in this build (`docs/known-issues.md`). The metadata is shown
 * instead of an `<img>` pointed at a URL that would 404, because a broken image
 * icon reads as a corrupt upload rather than as an absent delivery route.
 */
export function ProductImageManager({
  images,
  isUploading,
  isMutating,
  errorMessage,
  onUpload,
  onSetPrimary,
  onMove,
  onDelete,
  onError,
}: ProductImageManagerProps) {
  const [makePrimary, setMakePrimary] = useState(false);
  const atLimit = images.length >= MAX_IMAGES_PER_PRODUCT;

  const run = async (action: () => Promise<void>) => {
    onError(null);
    try {
      await action();
    } catch {
      // The page's error handler has already recorded the message; nothing more to
      // do here, and swallowing keeps a failed image write from taking the whole
      // editor down with an unhandled rejection.
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Reset first so choosing the same file twice in a row still fires a change.
    event.target.value = "";
    if (!file) {
      return;
    }
    void run(() => onUpload(file, makePrimary));
  };

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= images.length) {
      return;
    }
    const ids = images.map((image) => image.id);
    const [moved] = ids.splice(index, 1);
    ids.splice(target, 0, moved as number);
    void run(() => onMove(ids));
  };

  return (
    <section aria-labelledby="product-images-heading" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="product-images-heading" className="text-sm font-semibold text-bolder-text">
          Images
        </h3>
        <p className="text-xs text-bolder-muted">
          {images.length} of {MAX_IMAGES_PER_PRODUCT} used
        </p>
      </div>

      <p className="text-xs text-bolder-muted">
        JPEG, PNG or WebP, up to 5&nbsp;MB. The server checks the file's contents, then resizes and
        re-encodes it — your original file is never stored as uploaded.
      </p>

      {errorMessage && (
        <p role="alert" className="rounded-md bg-bolder-danger-surface p-3 text-sm text-bolder-danger">
          {errorMessage}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-bolder-text">
          <input
            type="checkbox"
            checked={makePrimary}
            onChange={(event) => setMakePrimary(event.target.checked)}
            className="h-4 w-4 rounded border-bolder-line-control text-bolder-rose-action focus:ring-bolder-rose"
          />
          Make the next upload the cover
        </label>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          disabled={atLimit || isUploading || isMutating}
          aria-label="Upload a product image"
          onChange={handleFileChange}
          className="text-sm text-bolder-muted file:mr-3 file:rounded-md file:border file:border-bolder-line-control file:bg-bolder-surface file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-bolder-text hover:file:bg-bolder-bg"
        />
        {isUploading && <span className="text-xs text-bolder-muted">Uploading...</span>}
      </div>

      {atLimit && (
        <p className="text-xs text-bolder-warning">
          This product already holds the maximum of {MAX_IMAGES_PER_PRODUCT} images. Remove one to
          upload another.
        </p>
      )}

      {images.length === 0 ? (
        <p className="rounded-md border border-dashed border-bolder-line-control p-4 text-sm text-bolder-muted">
          No images yet. The first image you upload becomes this product&rsquo;s cover automatically.
        </p>
      ) : (
        <ol className="space-y-2">
          {images.map((image, index) => (
            <li
              key={image.id}
              className="flex flex-wrap items-center gap-3 rounded-md border bolder-line bg-bolder-surface p-3"
            >
              <span className="w-6 text-xs font-medium text-bolder-muted">{index + 1}</span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-bolder-text">
                  {image.originalFilename ?? image.storageKey}
                </span>
                <span className="block text-xs text-bolder-muted">
                  {image.mimeType} · {formatFileSize(image.fileSize)}
                </span>
              </span>

              {image.primary ? (
                <StatusPill tone="bg-bolder-blush-light text-bolder-rose-deep" label="Cover" />
              ) : (
                <button
                  type="button"
                  disabled={isMutating}
                  onClick={() => void run(() => onSetPrimary(image.id))}
                  className={ACTION_BUTTON_CLASS}
                >
                  Make cover
                </button>
              )}

              <button
                type="button"
                aria-label={`Move ${image.originalFilename ?? image.storageKey} earlier`}
                disabled={index === 0 || isMutating}
                onClick={() => move(index, -1)}
                className={ACTION_BUTTON_CLASS}
              >
                Move up
              </button>
              <button
                type="button"
                aria-label={`Move ${image.originalFilename ?? image.storageKey} later`}
                disabled={index === images.length - 1 || isMutating}
                onClick={() => move(index, 1)}
                className={ACTION_BUTTON_CLASS}
              >
                Move down
              </button>
              <button
                type="button"
                disabled={isMutating}
                onClick={() => void run(() => onDelete(image.id))}
                className={`rounded-md border bolder-danger-border bg-bolder-danger-surface px-2 py-1 text-xs font-medium text-bolder-danger hover:bg-bolder-danger-surface disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
              >
                Delete
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}