import { Suspense, useMemo } from "react";
import { Link } from "react-router-dom";
import { AnimatedList } from "@/motion/AnimatedList";
import { FOCUS_RING_DARK } from "@/motion/pressable";
import { FeatureRow } from "@/features/home/components/FeatureRow";
import { HeroBackdrop } from "@/features/home/components/HeroBackdrop";
import { HeroFloatingCards } from "@/features/home/components/HeroFloatingCards";
import { HeroSearch } from "@/features/home/components/HeroSearch";
import { MapPinIcon } from "@/features/home/components/icons";
import { HeroFallback } from "@/features/home/hero/HeroFallback";
import { SceneErrorBoundary } from "@/features/home/hero/SceneErrorBoundary";
import { createRetryableLazy } from "@/features/home/hero/lazyWithRetry";

/**
 * Set once an attempt at the hero chunk has spent its retry, so a genuinely
 * missing asset costs one extra request per visit rather than a retry on every
 * render.
 */
const HERO_RETRY_FLAG = "fc-hero-chunk-retry";

/**
 * The 3D bouquet is a dynamic import, so Three.js and R3F are only fetched when
 * the landing page is actually visited and they land in their own chunk rather
 * than the initial `index-*.js` bundle.
 *
 * The attempt is built **per visit**, inside the component, through
 * `createRetryableLazy`. `React.lazy` caches a rejected payload and re-throws it
 * for the life of the component object, so an attempt hoisted to module scope —
 * the obvious way to write this — stays dead after its retry fails, and
 * navigating away and back replays the rejection instead of asking for the chunk
 * again. Building it here means each visit arrives with an empty cache and its
 * own one-shot retry budget. See `retryableLazy.test.tsx`.
 *
 * The Suspense fallback is the same static bouquet the WebGL and error paths
 * render, so the frame is filled — at the same size, so no layout shift — from
 * the first paint.
 *
 * The error boundary is imported eagerly and sits *above* the lazy boundary on
 * purpose. A boundary inside the chunk cannot catch that chunk failing to load,
 * and a rejected dynamic import throws while this component renders — so with
 * nothing above it, one failed chunk fetch would unmount the whole application
 * and take the heading, copy and calls to action with it. `SceneErrorBoundary`
 * imports nothing from Three, so hoisting it here keeps the main chunk clean.
 */
function useBouquetHero() {
  return useMemo(
    () =>
      createRetryableLazy(
        () => import("@/features/home/hero/BouquetHero"),
        HERO_RETRY_FLAG,
      ),
    [],
  );
}

function handleHeroChunkError(error: Error): void {
  console.error("[home] 3D bouquet chunk failed to load; showing the static fallback", error);
}

/**
 * Text for a reader who never sees the canvas. The bouquet is decoration, so it
 * carries no accessible name of its own; this is the sentence that carries its
 * meaning instead, which is why it is on the page rather than in an `alt`.
 */
const BOUQUET_DESCRIPTION =
  "A hand-tied bouquet of rose, blush and gold blooms with green foliage, wrapped in plum paper.";

const FLORIST_CTA =
  `inline-flex w-fit items-center gap-2 rounded-sm border-b border-bolder-leaf text-[15px] font-medium text-bolder-leaf-text transition-colors duration-micro ease-standard hover:border-bolder-leaf-text hover:text-bolder-leaf ${FOCUS_RING_DARK}`;

export function HomePage() {
  const { Component: BouquetHero } = useBouquetHero();

  return (
    <div className="relative isolate overflow-hidden bg-bolder-bg font-body text-bolder-text">
      <HeroBackdrop />

      <div className="relative mx-auto w-full max-w-landing px-5 sm:px-8 lg:px-14">
        {/*
          One column below `lg`, text first, then the bouquet. The overflow clip
          is here as well as on the page: the decorative rings and glass cards
          overhang their column by design, and a hero that can scroll sideways is
          worse than a ring that gets cut.
        */}
        <section aria-labelledby="landing-heading" className="overflow-hidden py-9">
          <div className="grid items-center gap-10 lg:grid-cols-[1.05fr_0.95fr]">
            {/*
              Entry order: badge, heading, supporting copy, search, florist call
              to action — `AnimatedList` schedules its children one stagger
              interval apart using the shared variants, so this is the same motion
              the rest of the app already runs and it collapses to nothing under
              `prefers-reduced-motion`.
            */}
            <AnimatedList className="flex min-w-0 flex-col gap-7">
              <span className="inline-flex w-fit items-center gap-2 rounded-pill border border-glass-border bg-glass px-4 py-2 text-[14px] font-medium text-bolder-blush">
                <MapPinIcon className="h-4 w-4" />
                Local florists near you
              </span>

              <h1
                id="landing-heading"
                className="font-display text-[40px] font-normal leading-none tracking-[-0.025em] text-bolder-text sm:text-[52px] lg:text-[84px]"
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
            </AnimatedList>

            <div className="relative min-w-0">
              {/* Decorative rings, behind the bouquet. Desktop-only: below the
                  two-column breakpoint a 620px ring would sit under the copy. */}
              <span aria-hidden="true" className="fc-ring fc-ring-solid hidden lg:block" />
              <span aria-hidden="true" className="fc-ring fc-ring-dashed hidden lg:block" />

              <SceneErrorBoundary fallback={<HeroFallback />} onError={handleHeroChunkError}>
                <Suspense fallback={<HeroFallback />}>
                  <BouquetHero />
                </Suspense>
              </SceneErrorBoundary>

              <HeroFloatingCards />

              <p className="sr-only">{BOUQUET_DESCRIPTION}</p>
            </div>
          </div>
        </section>

        <FeatureRow />
      </div>
    </div>
  );
}
