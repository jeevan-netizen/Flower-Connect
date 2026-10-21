import type { PublicProductImage } from "@/features/customer/types";
import { formatFileSize } from "@/features/customer/format";

interface ProductImagePlaceholderProps {
  /** The product's first image, or undefined when it has none. */
  image: PublicProductImage | undefined;
  productName: string;
  className?: string;
}

/**
 * A stand-in for a product photo.
 *
 * No route serves the image bytes (known issue 020, D-31), so this never
 * renders an `<img>`: a broken image reads as a failed upload rather than as
 * a missing delivery route, and the storage key that would build the URL is
 * deliberately absent from the public payload. What is shown instead is the
 * metadata the backend already sends — filename, type and size — so the
 * customer can see that a photo exists.
 */
export function ProductImagePlaceholder({
  image,
  productName,
  className = "",
}: ProductImagePlaceholderProps) {
  const label = image
    ? `Photo of ${productName}: ${image.originalFilename ?? "unnamed file"}, ${image.mimeType}`
    : `No photo available for ${productName}`;

  return (
    <div
      role="img"
      aria-label={label}
      className={`flex aspect-[4/3] w-full flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3 text-center ${className}`}
    >
      <span className="text-xs font-medium uppercase tracking-wide text-slate-500">Photo</span>
      {image ? (
        <>
          <span className="w-full truncate text-sm font-medium text-slate-700">
            {image.originalFilename ?? "Unnamed file"}
          </span>
          <span className="text-xs text-slate-500">
            {image.mimeType} · {formatFileSize(image.fileSize)}
          </span>
        </>
      ) : (
        <span className="text-sm text-slate-500">No photo yet</span>
      )}
    </div>
  );
}
