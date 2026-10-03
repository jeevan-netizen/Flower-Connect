/**
 * Placement of the six drifting petals, right half of the hero beside the
 * bouquet. The geometry itself — size, offset, opacity — lives in the
 * `.fc-petal-*` rules in `src/app/styles/index.css`, so this list is nothing but
 * the class each petal wears.
 */
const PETALS = [
  "fc-petal-1",
  "fc-petal-2",
  "fc-petal-3",
  "fc-petal-4",
  "fc-petal-5",
  "fc-petal-6",
] as const;

/**
 * The landing page's background decoration: two radial glows, a display-type
 * watermark and six drifting petals.
 *
 * The whole layer is `aria-hidden` and `pointer-events-none`, which is the point:
 * it must never be reachable by a screen reader or by a pointer, and the glows
 * overhang their container by design, so the parent clips it rather than letting
 * it start a horizontal scroll.
 *
 * Desktop-only (the petals and the watermark are hidden below `lg` by their own
 * classes): on a narrow screen the bouquet is stacked under the copy and there is
 * no room to decorate without decoration landing on top of text.
 */
export function HeroBackdrop() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <span className="fc-glow fc-glow-rose" />
      <span className="fc-glow fc-glow-leaf" />
      <span className="fc-watermark hidden lg:block">Bloom</span>
      {PETALS.map((placement) => (
        <span key={placement} className={`fc-petal fc-petal-drift hidden lg:block ${placement}`} />
      ))}
    </div>
  );
}
