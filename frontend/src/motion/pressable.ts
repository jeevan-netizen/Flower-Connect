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

/**
 * The focus ring for the dark landing surface.
 *
 * Same shape as `FOCUS_RING` — the ring itself is still two pixels of an accent
 * colour plus a two-pixel offset — but the accent and the offset come from the
 * landing tokens. A green ring with a green offset reads as a halo on the dark
 * header and hero, so the surface supplies its own pair here instead of every
 * control on that surface hand-rolling one.
 */
export const FOCUS_RING_DARK =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bolder-rose focus-visible:ring-offset-2 focus-visible:ring-offset-bolder-bg";

/**
 * The transition a text control, textarea or select wears so its border colour
 * and focus shadow slide rather than snap.
 *
 * Applied next to `focus:border-brand-500 focus:ring-brand-500`: without it the
 * focus ring appears instantly, which reads as a border colour change rather
 * than as focus arriving. Only colour and shadow are transitioned, never the
 * control's box, and the `prefers-reduced-motion` block in `index.css` collapses
 * the duration to nothing for anyone who has asked for that.
 */
export const FIELD_TRANSITION =
  "transition-[border-color,box-shadow] duration-micro ease-standard";