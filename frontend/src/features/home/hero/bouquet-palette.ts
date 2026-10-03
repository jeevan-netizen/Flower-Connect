/**
 * Scene and fallback colours for the landing hero, mirrored from the landing
 * design tokens.
 *
 * The 3D scene is the one place in the app that cannot read a CSS custom
 * property: Three.js materials and `setColorAt` need literal colour strings at
 * construction time, long before any style is resolved. Rather than let the
 * bouquet drift onto its own private palette, this module is the single mirror
 * of the `--color-*` tokens in `src/app/styles/index.css`, exactly the way
 * `src/motion/tokens.ts` mirrors `--fc-motion-*`.
 *
 * `TOKENS` is the whole mirror, keyed by the token name it stands for, and both
 * export objects below are views onto it — so there is one value per token and no
 * way for the stem colour and a bloom colour to disagree. `bouquet-palette.test.ts`
 * reads the stylesheet and fails if the two ever disagree, so the mirror cannot
 * rot silently.
 *
 * Every value here is a token, not a brand decision: a light landing theme
 * changes `:root`, and this file changes with it.
 */

/** Token name to value — the mirror of the landing `--color-*` tokens. */
export const TOKENS = {
  "--color-rose": "#f27a9b",
  "--color-rose-strong": "#e8567f",
  "--color-rose-deep": "#d63f6c",
  "--color-blush": "#f7b8c8",
  "--color-blush-light": "#fac1d0",
  "--color-leaf": "#5fae91",
  "--color-leaf-text": "#8fd0b6",
  "--color-leaf-dark": "#3e8f73",
  "--color-gold": "#f2b84b",
  "--color-plum": "#3a1b33",
} as const;

/** Structural colours: stems, foliage and the paper wrap. */
export const SCENE_COLORS = {
  /** `--color-leaf-dark`: stems read darker than the leaves they carry. */
  stem: TOKENS["--color-leaf-dark"],
  /** `--color-leaf`: foliage. */
  leaf: TOKENS["--color-leaf"],
  /** `--color-plum`: the wrapped-paper cone. */
  wrap: TOKENS["--color-plum"],
  /** `--color-rose-deep`: the wrap's trim, so the cone is not one flat shape. */
  wrapTrim: TOKENS["--color-rose-deep"],
} as const;

/**
 * Bloom palette — plum, pink, rose and gold as the design calls for, plus the
 * two greens the foliage needs. Keys match the token names so a colour is
 * traceable from a placement in `bouquet-layout` back to `--color-…`.
 */
export const BLOOM_COLORS = {
  rose: TOKENS["--color-rose"],
  roseStrong: TOKENS["--color-rose-strong"],
  roseDeep: TOKENS["--color-rose-deep"],
  blush: TOKENS["--color-blush"],
  blushLight: TOKENS["--color-blush-light"],
  gold: TOKENS["--color-gold"],
  plum: TOKENS["--color-plum"],
  leaf: TOKENS["--color-leaf"],
  leafText: TOKENS["--color-leaf-text"],
  leafDark: TOKENS["--color-leaf-dark"],
} as const;
