import { Link } from "react-router-dom";
import { AnimatedList } from "@/motion/AnimatedList";
import { FOCUS_RING_DARK } from "@/motion/pressable";
import { FeatureRow } from "@/features/home/components/FeatureRow";
import { HeroBackdrop } from "@/features/home/components/HeroBackdrop";
import { HeroSearch } from "@/features/home/components/HeroSearch";
import { MapPinIcon } from "@/features/home/components/icons";
import { BotanicalHero } from "@/shared/ui/Botanical";

/**
 * Text for a reader who never sees the illustration. The bouquet is decoration,
 * so it carries no accessible name of its own; this is the sentence that carries
 * its meaning instead, which is why it is on the page rather than in an `alt`.
 */
const BOUQUET_DESCRIPTION =
  "A soft bouquet of pink, peach and cream flowers with green foliage in a fluted cream ceramic vase.";

const FLORIST_CTA =
  `inline-flex w-fit items-center gap-2 rounded-sm border-b border-bolder-leaf text-[15px] font-medium text-bolder-leaf-text transition-colors duration-micro ease-standard hover:border-bolder-leaf-text hover:text-bolder-leaf-dark ${FOCUS_RING_DARK}`;

/**
 * The public landing page.
 *
 * The hero uses the flower artwork cropped from the supplied design reference.
 * The layout is one column below `lg` and text-first at
 * every width, so the heading, delivery picker, and account entry points remain
 * readable before the decorative bouquet.
 */
export function HomePage() {
  return (
    <div className="relative isolate overflow-hidden bg-bolder-bg font-body text-bolder-text">
      <HeroBackdrop />

      <div className="relative mx-auto w-full max-w-landing px-5 sm:px-8 lg:px-14">
        <section aria-labelledby="landing-heading" className="py-10 lg:py-14">
          <div className="grid items-center gap-12 lg:grid-cols-[1.05fr_0.95fr]">
            {/*
              Entry order: badge, heading, supporting copy, search, florist call
              to action — `AnimatedList` schedules its children one stagger
              interval apart using the shared variants, so this is the same motion
              the rest of the app already runs and it collapses to nothing under
              `prefers-reduced-motion`.
            */}
            <AnimatedList className="flex min-w-0 flex-col gap-6">
              <span className="inline-flex w-fit items-center gap-2 rounded-pill border border-bolder-line bg-bolder-surface px-4 py-2 text-[14px] font-medium text-bolder-rose-strong shadow-card">
                <MapPinIcon className="h-4 w-4" />
                Local florists near you
              </span>

              <h1
                id="landing-heading"
                className="font-display text-[44px] font-normal leading-[0.98] tracking-[-0.03em] text-bolder-text sm:text-[56px] lg:text-[76px]"
              >
                Flowers,{" "}
                <em className="font-display italic text-bolder-rose">beautifully</em> delivered.
              </h1>

              <p className="max-w-[470px] text-[19px] leading-[1.55] text-bolder-muted">
                Discover independent florists in your neighbourhood and send something meaningful,
                today or on the day that matters.
              </p>

              <HeroSearch />

              <div className="flex flex-col gap-2">
                <Link className={FLORIST_CTA} to="/vendor/register">
                  Become a Florist
                  <span aria-hidden="true">&rarr;</span>
                </Link>
                <p className="text-[14px] text-bolder-muted">
                  Selling flowers? Open your shop in minutes.
                </p>
              </div>

              <div className="flex flex-col gap-2">
                <Link
                  className={FLORIST_CTA}
                  to="/login"
                  state={{ from: "/farmer", farmerEntry: true }}
                >
                  Farmer sign-in
                  <span aria-hidden="true">&rarr;</span>
                </Link>
                <p className="text-[14px] text-bolder-muted">
                  Supplying florists? Track your deposits and collections.
                </p>
              </div>

              <div className="flex flex-col gap-2">
                <Link className={FLORIST_CTA} to="/farmer/register">
                  Register as a Farmer
                  <span aria-hidden="true">&rarr;</span>
                </Link>
                <p className="text-[14px] text-bolder-muted">
                  Growing flowers? Join the network and sell to local florists.
                </p>
              </div>
            </AnimatedList>

            <div className="relative min-w-0">
              <BotanicalHero className="fc-slide-up-in" />

              <p className="sr-only">{BOUQUET_DESCRIPTION}</p>
            </div>
          </div>
        </section>

        <FeatureRow />
      </div>
    </div>
  );
}
