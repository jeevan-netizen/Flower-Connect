import { useState } from "react";
import type { PublicProductImage } from "@/features/customer/types";
import { API_BASE_URL } from "@/shared/lib/api";

interface ProductImageProps {
  productId: number;
  /** The product's first/primary image, or undefined when it has none. */
  image: PublicProductImage | undefined;
  productName: string;
  /** The selected delivery location; the image endpoint requires one. Null means no image can be served. */
  locationId: number | null;
  className?: string;
}

/**
 * Renders a product image served by the backend, with a metadata placeholder
 * fallback when the product has no image, when no delivery location is
 * selected (the image endpoint requires one), or when the image fails to load.
 *
 * The URL uses the configured API origin because images are loaded by the
 * browser rather than Axios. The backend checks delivery distance and serves
 * the stored bytes; no storage key reaches the client.
 */
export function ProductImage({
  productId,
  image,
  productName,
  locationId,
  className = "",
}: ProductImageProps) {
  const [loadError, setLoadError] = useState(false);

  const src =
    locationId !== null && image !== undefined
      ? `${API_BASE_URL.replace(/\/+$/, "")}/products/${productId}/images/${image.id}?locationId=${locationId}`
      : null;

  const alt = `Photo of ${productName}: ${image?.originalFilename ?? "unnamed file"}`;
  const hasImage = src !== null && !loadError;

  return (
    <div
      role={hasImage ? undefined : "img"}
      aria-label={image ? alt : `No photo available for ${productName}`}
      className={`flex aspect-[4/3] w-full flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-bolder-line bg-bolder-bg p-3 text-center ${className}`}
    >
      {hasImage ? (
        <img
          src={src}
          alt={alt}
          onError={() => setLoadError(true)}
          className="w-full h-full object-cover rounded-lg"
        />
      ) : (
        <>
          <span className="text-xs font-medium uppercase tracking-wide text-bolder-muted">Photo</span>
          {image ? (
            <>
              <span className="w-full truncate text-sm font-medium text-bolder-text">
                {image.originalFilename ?? "Unnamed file"}
              </span>
              <span className="text-xs text-bolder-muted">
                {image.mimeType} · {image.fileSize} bytes
              </span>
            </>
          ) : (
            <span className="text-sm text-bolder-muted">No photo yet</span>
          )}
        </>
      )}
    </div>
  );
}
