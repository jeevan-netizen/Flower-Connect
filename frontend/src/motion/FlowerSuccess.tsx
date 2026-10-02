/** Five petals plus a drawn checkmark — no confetti, no canvas, no JS timers. */
const PETAL_ANGLES = [0, 72, 144, 216, 288];

interface FlowerSuccessProps {
  /** The confirmation text. This is the only part exposed to assistive tech. */
  message: string;
  className?: string;
}

/**
 * Success feedback: a small flower that blooms while its checkmark draws itself.
 *
 * The whole graphic is `aria-hidden`; the announcement is the text inside a
 * `role="status"` live region, which is how a screen reader reports the
 * success without describing a decorative animation. Everything is CSS
 * (`fc-bloom-in`, `fc-check-draw`), so the animation is switched off by the
 * `prefers-reduced-motion` rule in `src/app/styles/index.css` while the message
 * stays visible.
 */
export function FlowerSuccess({ message, className }: FlowerSuccessProps) {
  return (
    <div role="status" className={className ?? "flex items-center gap-2"}>
      <span aria-hidden="true" className="fc-bloom-in inline-flex h-6 w-6 shrink-0 text-brand-600">
        <svg viewBox="0 0 24 24" className="h-6 w-6" focusable="false">
          {PETAL_ANGLES.map((angle) => (
            <ellipse
              key={angle}
              cx="12"
              cy="6.6"
              rx="3.1"
              ry="4.6"
              fill="currentColor"
              opacity="0.35"
              transform={`rotate(${angle} 12 12)`}
            />
          ))}
          <circle cx="12" cy="12" r="3.1" fill="currentColor" />
          <path
            className="fc-check-draw stroke-white"
            d="M9.8 12.2l1.6 1.6 3-3.2"
            fill="none"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <span>{message}</span>
    </div>
  );
}
