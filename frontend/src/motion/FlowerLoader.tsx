interface FlowerLoaderProps {
  /** Announced to assistive tech; the petals themselves are decorative. */
  label?: string;
  /** Render `label` as visible text instead of visually-hidden text. */
  showLabel?: boolean;
  className?: string;
}

/**
 * A flower-inspired spinner, built entirely from two CSS rules
 * (`.fc-flower-spinner` and `fc-flower-spinner > span`) so it costs no
 * JavaScript and no Framer Motion render work.
 *
 * `role="status"` with visually-hidden text means a screen reader announces the
 * wait rather than an unlabelled graphic. Under
 * `prefers-reduced-motion: reduce` the global CSS rule stops the rotation while
 * the text still reports that something is loading — the loader keeps its
 * meaning even with no movement.
 */
export function FlowerLoader({
  label = "Loading",
  showLabel = false,
  className,
}: FlowerLoaderProps) {
  return (
    <div role="status" className={className ?? "flex items-center justify-center"}>
      <span aria-hidden="true" className="fc-flower-spinner shrink-0 text-brand-500">
        <span />
        <span />
        <span />
        <span />
        <span />
        <span />
      </span>
      <span className={showLabel ? "ml-3" : "sr-only"}>{label}</span>
    </div>
  );
}
