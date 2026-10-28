/**
 * Accessible star rating display (plan Phase 12, task 12.3).
 *
 * Read-only mode renders stars plus a text summary. Interactive mode lets the
 * customer pick a rating 1-5. When there are no reviews, the empty label is
 * shown instead of stars, matching the existing `formatCustomerRating` "New"
 * convention. Uses inline SVG so no icon dependency is added.
 */
export function StarRating({
  rating,
  reviewCount,
  interactive = false,
  value,
  onSelect,
  emptyLabel = "New",
  className,
}: {
  rating: number | null;
  reviewCount: number;
  interactive?: boolean;
  value?: number;
  onSelect?: (rating: number) => void;
  emptyLabel?: string;
  className?: string;
}) {
  const hasReviews = rating != null && reviewCount > 0;
  const displayRating = interactive ? (value ?? 0) : (rating ?? 0);
  const fullStars = Math.floor(displayRating);
  const hasHalfStar = displayRating % 1 >= 0.5;
  const emptyStars = 5 - fullStars - (hasHalfStar ? 1 : 0);

  const star = (filled: boolean, half: boolean) => (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      {filled
        ? half
          ? <polyline points="12 3 22 9 14 19 6 19 2 9 12 3" />
          : <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.75 5.82 21.02 9 14.14 22 9.27 15.09 8.26 12 2" />
        : <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.75 5.82 21.02 9 14.14 22 9.27 15.09 8.26 12 2" strokeOpacity="0.2" />}
    </svg>
  );

  const stars = [];
  for (let i = 0; i < fullStars; i++) {
    stars.push(star(true, false));
  }
  if (hasHalfStar) {
    stars.push(star(true, true));
  }
  for (let i = 0; i < emptyStars; i++) {
    stars.push(star(false, false));
  }

  if (!hasReviews && !interactive) {
    return (
      <span
        className={`inline-flex items-center gap-1 text-slate-500 text-sm ${className ?? ""}`}
        aria-label="No reviews yet"
      >
        {emptyLabel}
      </span>
    );
  }

  const label = hasReviews
    ? `${rating!.toFixed(1)} stars (${reviewCount} review${reviewCount === 1 ? "" : "s"})`
    : `${emptyLabel}`;

  return (
    <div
      className={`inline-flex items-center gap-1 ${className ?? ""}`}
      role="img"
      aria-label={label}
    >
      {interactive
        ? stars.map((star, idx) => {
            const starValue = idx + 1;
            return (
              <button
                key={idx}
                type="button"
                aria-label={`Rate ${starValue} of 5`}
                aria-pressed={value === starValue}
                onClick={() => onSelect?.(starValue)}
                className={`p-1 text-slate-300 hover:text-amber-400 focus:outline-none focus:ring-2 focus:ring-amber-500 rounded ${
                  starValue <= (value ?? 0) ? "text-amber-400" : ""
                }`}
              >
                {star}
              </button>
            );
          })
        : stars}
      <span className="ml-1 text-sm text-slate-600">{label}</span>
    </div>
  );
}
