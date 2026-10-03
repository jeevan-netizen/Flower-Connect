import { Fragment } from "react";
import { AnimatedList } from "@/motion/AnimatedList";
import { CalendarIcon, CheckCircleIcon, ClockIcon, ShopIcon } from "./icons";

interface Feature {
  readonly key: string;
  readonly title: string;
  readonly subtitle: string;
  readonly Icon: typeof ShopIcon;
  /** Icon colour, as a token class. */
  readonly accent: string;
}

/**
 * The four capabilities the product actually has on the way in: local vendors,
 * same-day and scheduled delivery, and a simple order flow. Deliberately no
 * counts, ratings or testimonials — none of those exist yet, and a placeholder
 * number on a landing page is a claim nobody has earned.
 */
const FEATURES: readonly Feature[] = [
  {
    key: "local",
    title: "Local florists",
    subtitle: "Independent shops, nearby",
    Icon: ShopIcon,
    accent: "text-bolder-rose",
  },
  {
    key: "same-day",
    title: "Same-day delivery",
    subtitle: "When the florist is open",
    Icon: ClockIcon,
    accent: "text-bolder-leaf-text",
  },
  {
    key: "scheduled",
    title: "Scheduled delivery",
    subtitle: "Choose the day and slot",
    Icon: CalendarIcon,
    accent: "text-bolder-gold",
  },
  {
    key: "ordering",
    title: "Simple ordering",
    subtitle: "A few taps to checkout",
    Icon: CheckCircleIcon,
    accent: "text-bolder-blush",
  },
];

/** Glass-soft surface shared by the four cards. No `backdrop-filter` here. */
const CARD =
  "flex items-center gap-[14px] rounded-card border border-glass-border-soft bg-glass-soft p-[22px]";

/**
 * The feature row below the hero.
 *
 * `AnimatedList` supplies the staggered entry, and its wrapper is the grid item,
 * so the row reuses the shared stagger rather than introducing a second one. The
 * grid goes 4 → 2 → 1 columns across the two breakpoints, and every card is the
 * same markup at every width.
 */
export function FeatureRow() {
  return (
    <section aria-labelledby="landing-features-heading" className="pb-9">
      <h2 id="landing-features-heading" className="sr-only">
        What FlowerConnect does
      </h2>
      <AnimatedList
        className="grid grid-cols-1 gap-[18px] sm:grid-cols-2 lg:grid-cols-4"
        itemClassName={CARD}
      >
        {FEATURES.map(({ key, title, subtitle, Icon, accent }) => (
          // The card itself is the animated wrapper; this fragment is its content.
          <Fragment key={key}>
            <Icon className={`h-7 w-7 shrink-0 ${accent}`} />
            <div className="min-w-0">
              <p className="text-[15px] font-semibold leading-tight text-bolder-text">{title}</p>
              <p className="mt-1 text-[13px] leading-snug text-bolder-muted">{subtitle}</p>
            </div>
          </Fragment>
        ))}
      </AnimatedList>
    </section>
  );
}
