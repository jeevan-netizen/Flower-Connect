import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

/**
 * Shared class recipes for the editorial design system.
 *
 * These are strings, not components: a recipe changes how an existing control
 * looks without changing its markup, its accessible name or its behaviour.
 * Screens compose them next to their own layout classes, which keeps a button
 * in a table and a button in a dialog on the same token set.
 *
 * Every value resolves from `tailwind.config.ts` / `src/app/styles/index.css`.
 * A hex value here would be a bug: the token layer is the only place a colour
 * is chosen.
 */

const BUTTON_BASE = [
  "inline-flex items-center justify-center gap-2 rounded-control px-5 py-2.5",
  "text-sm font-medium leading-none",
  "transition-[background-color,border-color,color,box-shadow,transform] duration-micro ease-standard",
  "disabled:cursor-not-allowed disabled:opacity-60",
  "motion-reduce:transition-none",
  PRESSABLE,
  FOCUS_RING,
].join(" ");

/** The primary action. Rose, never green. */
export const BUTTON_PRIMARY = `${BUTTON_BASE} border border-transparent bg-bolder-rose-action text-white hover:bg-bolder-rose-deep`;

/** A secondary action on a paper surface. */
export const BUTTON_SECONDARY = `${BUTTON_BASE} border border-bolder-line-control bg-bolder-surface text-bolder-text hover:bg-bolder-surface-muted`;

/** A low-emphasis action. */
export const BUTTON_GHOST = `${BUTTON_BASE} border border-transparent bg-transparent text-bolder-rose-strong hover:bg-bolder-blush-light`;

/** A destructive action. */
export const BUTTON_DANGER = `${BUTTON_BASE} border border-transparent bg-danger text-white hover:bg-danger/90`;

/** The shared form control. The border is the 3:1 control token. */
export const CONTROL = [
  "block w-full rounded-control border border-bolder-line-control bg-bolder-surface",
  "px-3 py-2 text-sm text-bolder-text shadow-sm",
  "placeholder:text-bolder-placeholder",
  "transition-[border-color,box-shadow] duration-micro ease-standard",
  "focus:border-bolder-rose focus:outline-none focus:ring-2 focus:ring-bolder-rose/30",
  "disabled:cursor-not-allowed disabled:bg-disabled-surface disabled:text-disabled",
].join(" ");

/** Applied alongside `CONTROL` when the field has a validation message. */
export const CONTROL_ERROR = "border-danger focus:border-danger focus:ring-danger/25";

/** A card surface with no padding, for a table or a flush image. */
export const CARD = "rounded-card border border-bolder-line bg-bolder-surface shadow-card";

/** A card with the standard inner padding. */
export const CARD_PADDED = `${CARD} p-5 sm:p-6`;

/** A dashboard statistic tile. */
export const STAT_TILE = `${CARD} p-4 sm:p-5`;

/** The centred page frame used by every non-shell screen. */
export const PAGE_CONTAINER =
  "mx-auto w-full max-w-content px-5 py-10 sm:px-8 lg:py-14";

/** The one heading pattern, so page titles cannot drift. */
export const PAGE_TITLE =
  "font-display text-3xl font-normal leading-tight tracking-[-0.02em] text-bolder-text sm:text-4xl";

export const PAGE_DESCRIPTION = "mt-2 max-w-2xl text-sm leading-relaxed text-bolder-muted";

/** A horizontal rule that is not a control boundary. */
export const DIVIDER = "border-bolder-line";

export const TABLE_WRAP = "overflow-x-auto rounded-card border border-bolder-line bg-bolder-surface shadow-card";
export const TABLE = "min-w-full text-left text-sm text-bolder-text";
export const THEAD = "border-b border-bolder-line bg-bolder-surface-muted text-xs font-medium text-bolder-muted";
export const TH = "whitespace-nowrap px-4 py-3";
export const TD = "px-4 py-3 align-top";
export const TABLE_ROW = "border-b border-bolder-line last:border-0 hover:bg-bolder-surface-muted/60";

export const BADGE_BASE =
  "inline-flex items-center gap-1 rounded-chip border px-2.5 py-1 text-xs font-medium leading-none";

/** Status alias map. Every badge uses a semantic pair, never `brand`. */
export const BADGE_TONES = {
  success: "border-success-border bg-success-surface text-success",
  warning: "border-warning-border bg-warning-surface text-warning",
  danger: "border-danger-border bg-danger-surface text-danger",
  info: "border-info-border bg-info-surface text-info",
  neutral: "border-neutral-border bg-neutral-surface text-neutral",
} as const;

export type BadgeTone = keyof typeof BADGE_TONES;

export const ALERT_BASE = "rounded-card border p-4 text-sm";
export const ALERT_TONES = {
  success: "border-success-border bg-success-surface text-success",
  warning: "border-warning-border bg-warning-surface text-warning",
  danger: "border-danger-border bg-danger-surface text-danger",
  info: "border-info-border bg-info-surface text-info",
  neutral: "border-neutral-border bg-neutral-surface text-neutral",
} as const;

export const OVERLAY = "fixed inset-0 z-50 overflow-y-auto bg-bolder-overlay p-4 sm:p-8";

export const DIALOG =
  "mx-auto w-full max-w-lg rounded-card border border-bolder-line bg-bolder-surface p-5 shadow-glass sm:p-6";

export const EMPTY_STATE = `${CARD} p-8 text-center`;
export const EMPTY_TITLE = "font-display text-xl font-normal text-bolder-text";
export const EMPTY_DESCRIPTION = "mx-auto mt-2 max-w-md text-sm leading-relaxed text-bolder-muted";

export const FILTER_BAR = `${CARD} p-4 sm:p-5`;

/** A sidebar or dashboard navigation link. */
export function navLinkClass(isActive: boolean): string {
  return [
    "block rounded-control px-3 py-2 text-sm font-medium",
    "transition-colors duration-micro ease-standard",
    FOCUS_RING,
    isActive
      ? "bg-bolder-rose-action text-white"
      : "text-bolder-text hover:bg-bolder-surface-muted hover:text-bolder-rose-strong",
  ].join(" ");
}
