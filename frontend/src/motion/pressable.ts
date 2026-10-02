/**
 * The press-feedback class every interactive control in the app shares.
 *
 * Pressing a control is the only affordance a pointer user has that hover does
 * not provide, and the `motion-reduce:` half is what keeps it from being an
 * unwanted movement for someone who has asked for reduced motion. Both halves
 * are derived from the `scale.press` distance token in `tokens.ts`, so a control
 * that hand-writes its own variant instead of composing this string silently
 * drifts from the rest of the product.
 *
 * Kept dependency-free and in `motion/` so any feature can import it without
 * reaching across feature slices.
 */
export const PRESSABLE =
  "transition-[background-color,border-color,color,box-shadow,transform] duration-micro ease-standard active:scale-press motion-reduce:active:scale-100";

/** The focus ring shared by every control, so keyboard focus looks the same app-wide. */
export const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2";