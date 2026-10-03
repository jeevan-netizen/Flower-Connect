import { ClockIcon, ShopIcon } from "./icons";

interface FloatingCard {
  readonly key: string;
  readonly title: string;
  readonly subtitle: string;
  readonly Icon: typeof ClockIcon;
  /** Tile tint behind the icon, as a token class. */
  readonly tile: string;
  /** Corner of the hero column the card sits in. */
  readonly position: string;
}

/**
 * Copy and placement are the design's; the glass recipe (blur, border, radius,
 * padding, shadow, solid fallback) is `.fc-glass` plus the radius and shadow
 * tokens, so the two cards cannot drift apart from each other.
 *
 * These are the only elements in the app that use `backdrop-filter`: two small
 * cards over a mostly static hero, rather than a page-wide blur repainted on
 * every scroll frame.
 */
const CARDS: readonly FloatingCard[] = [
  {
    key: "same-day",
    title: "Same-day slots",
    subtitle: "Pick a delivery window",
    Icon: ClockIcon,
    tile: "bg-bolder-tile-leaf text-bolder-leaf-text",
    position: "left-[-10px] top-[90px]",
  },
  {
    key: "arranged",
    title: "Arranged locally",
    subtitle: "By florists you can trust",
    Icon: ShopIcon,
    tile: "bg-bolder-tile-rose text-bolder-rose",
    position: "right-[-6px] bottom-[90px]",
  },
];

/**
 * Two glass notes over the bouquet.
 *
 * They are real content, not decoration, so they are not hidden from assistive
 * technology — they are the only place the page states what a same-day slot and
 * a locally arranged bouquet are. They hold no controls, so the whole layer is
 * `pointer-events-none`: a card that drifted under the cursor must not take the
 * pointer away from the canvas or from the copy beside it.
 *
 * Desktop-only. Below the two-column breakpoint the bouquet sits directly under
 * the heading, and an absolutely positioned card would sit on top of the words it
 * is meant to support.
 */
export function HeroFloatingCards() {
  return (
    <div className="pointer-events-none absolute inset-0 hidden lg:block">
      {CARDS.map(({ key, title, subtitle, Icon, tile, position }) => (
        <div
          key={key}
          className={`fc-glass fc-glass-float pointer-events-none absolute w-[198px] rounded-glass-card p-3 pr-[18px] shadow-glass ${position}`}
        >
          <div className="flex items-center gap-3">
            <span
              className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-chip ${tile}`}
            >
              <Icon className="h-[18px] w-[18px]" />
            </span>
            <div className="min-w-0">
              <p className="text-[13px] font-semibold leading-tight text-bolder-text">{title}</p>
              <p className="mt-0.5 text-[12px] leading-tight text-bolder-muted">{subtitle}</p>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
