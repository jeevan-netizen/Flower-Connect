import { useState } from "react";
import type { PublicProductImage } from "@/features/customer/types";

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
 * The URL is built from the product and image ids plus the selected location,
 * so no storage key ever reaches the client. The request goes through the
 * same /api proxy as every other API call, so it is same-origin in both dev
 * (Vite proxy) and prod (server reverse proxy) and satisfies img-src 'self'.
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
      ? `/api/v1/products/${productId}/images/${image.id}?locationId=${locationId}`
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
