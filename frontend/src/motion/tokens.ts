import type { Transition, Variants } from "framer-motion";

/**
 * Motion tokens — the single source of truth for every duration, easing,
 * stagger interval and travel distance used by the motion layer.
 *
 * Nothing in a component may hardcode one of these numbers. CSS mirrors the
 * same values as custom properties in `src/app/styles/index.css`
 * (`--fc-motion-*`) and Tailwind exposes them as `duration-*` / `ease-*`
 * utilities, so a JavaScript animation and a CSS transition of the same name
 * cannot drift apart.
 *
 * Durations are in seconds, because that is what Framer Motion's `transition`
 * takes. The CSS variables carry the same numbers as `ms`.
 */
export const durations = {
  /** Micro-feedback: hover, press, focus rings. */
  micro: 0.15,
  /** UI state: banners, toasts, inline reveals, route exits. */
  ui: 0.24,
  /** Route-level entry. Slow enough to read as a page change, short enough that navigation never feels gated. */
  page: 0.32,
  /** Slow, continuous loops (the flower loader). */
  ambient: 6,
} as const;

export type Easing = [number, number, number, number];

export const easings: Record<"standard" | "enter" | "exit", Easing> = {
  /** General purpose: decelerating, symmetric enough for enter and exit. */
  standard: [0.22, 0.61, 0.36, 1],
  /** Expressive ease-out for things entering the screen. */
  enter: [0.16, 1, 0.3, 1],
  /** Accelerating ease-in for things leaving it. */
  exit: [0.4, 0, 1, 1],
};

/** Delay added per child by `listContainerVariants`. */
export const staggerInterval = 0.05;

/** Travel distances, in pixels. Positive `enter` moves down into place. */
export const distances = {
  enter: 12,
  exit: -8,
  /** Hover lift applied by `AnimatedCard`. */
  lift: -2,
  /** Press feedback applied to pressable motion elements. */
  press: 0.98,
} as const;

/** Ambient spin period for `FlowerLoader`, in seconds (mirrors `--fc-motion-duration-ambient`). */
export const ambientSpinSeconds = durations.ambient;

const standard: Transition = { duration: durations.ui, ease: easings.standard };
const entering: Transition = { duration: durations.ui, ease: easings.enter };
const leaving: Transition = { duration: durations.micro, ease: easings.exit };

export const fadeVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: standard },
  exit: { opacity: 0, transition: leaving },
};

export const slideUpVariants: Variants = {
  hidden: { opacity: 0, y: distances.enter },
  visible: { opacity: 1, y: 0, transition: entering },
  exit: { opacity: 0, y: distances.exit, transition: leaving },
};

/** Route transition: `initial` → `animate` on entry, `exit` on navigation away. */
export const pageVariants: Variants = {
  initial: { opacity: 0, y: distances.enter },
  animate: { opacity: 1, y: 0, transition: { duration: durations.page, ease: easings.enter } },
  exit: { opacity: 0, y: distances.exit, transition: { duration: durations.ui, ease: easings.exit } },
};

/** Parent of `AnimatedList`: staggers its children's variant start. */
export const listContainerVariants: Variants = {
  hidden: {},
  visible: { transition: { staggerChildren: staggerInterval } },
};

export const listItemVariants: Variants = {
  hidden: { opacity: 0, y: distances.enter },
  visible: { opacity: 1, y: 0, transition: entering },
};

/** `AnimatedCard` — entry plus a small hover lift and a press scale. */
export const cardVariants: Variants = {
  hidden: { opacity: 0, y: distances.enter },
  visible: { opacity: 1, y: 0, transition: entering },
  hover: { y: distances.lift, transition: standard },
};

/** Modal/dialog enter-exit, shared by `ReasonDialog`. */
export const dialogVariants: Variants = {
  hidden: { opacity: 0, scale: distances.press },
  visible: { opacity: 1, scale: 1, transition: standard },
  exit: { opacity: 0, scale: distances.press, transition: leaving },
};

export const backdropVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: standard },
  exit: { opacity: 0, transition: leaving },
};
