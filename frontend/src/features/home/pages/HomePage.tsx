import { Suspense, lazy } from "react";
import { Link, useLocation } from "react-router-dom";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { HeroFallback } from "@/features/home/hero/HeroFallback";
import { SceneErrorBoundary } from "@/features/home/hero/SceneErrorBoundary";
import { lazyWithRetry } from "@/features/home/hero/lazyWithRetry";

/**
 * Set once the hero chunk retry has been spent, so a genuinely missing asset
 * costs one extra request rather than a retry on every render.
 */
const HERO_RETRY_FLAG = "fc-hero-chunk-retry";

/**
 * The 3D bouquet is a dynamic import, so Three.js and R3F are only fetched when
 * the public landing page is actually visited and they land in their own chunk
 * rather than the initial `index-*.js` bundle.
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
 *
 * The import is retried exactly once (`lazyWithRetry`). `React.lazy` caches a
 * rejected payload and re-throws it, so without this a single dropped request
 * would cost the visitor the hero until a full page reload.
 */
const BouquetHero = lazy(() =>
  lazyWithRetry(() => import("@/features/home/hero/BouquetHero"), HERO_RETRY_FLAG),
);

function handleHeroChunkError(error: Error): void {
  console.error("[home] 3D bouquet chunk failed to load; showing the static fallback", error);
}

const PRIMARY_CTA =
  `inline-flex items-center justify-center rounded-md bg-brand-600 px-5 py-3 text-sm font-semibold text-white hover:bg-brand-700 ${FOCUS_RING} ${PRESSABLE}`;

const SECONDARY_CTA =
  `inline-flex items-center justify-center rounded-md border border-brand-600 px-5 py-3 text-sm font-semibold text-brand-700 hover:bg-brand-50 ${FOCUS_RING} ${PRESSABLE}`;

export function HomePage() {
  // `location.key` changes on every navigation, which is exactly the signal the
  // boundary needs: coming back to `/` deserves a fresh attempt at the chunk.
  const location = useLocation();

  return (
    <FadeIn className="mx-auto w-full max-w-7xl px-4 py-12">
      <div className="grid items-center gap-10 lg:grid-cols-2">
        <div className="order-2 min-w-0 text-center lg:order-1 lg:text-left">
          <h1 className="text-4xl font-bold text-brand-700 sm:text-5xl">FlowerConnect</h1>
          <p className="mt-3 text-lg text-slate-600">Hyperlocal flower marketplace</p>
          <p className="mt-4 max-w-prose text-slate-600">
            Order same-day bouquets from florists around your neighbourhood, or list your shop and
            sell to nearby customers.
          </p>
          <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-center lg:justify-start">
            <Link className={PRIMARY_CTA} to="/browse">
              Browse flowers
            </Link>
            <Link className={SECONDARY_CTA} to="/register">
              Create an account
            </Link>
          </div>
        </div>

        <div className="order-1 min-w-0 lg:order-2">
          <SceneErrorBoundary
            fallback={<HeroFallback />}
            onError={handleHeroChunkError}
            resetKeys={[location.key]}
          >
            <Suspense fallback={<HeroFallback />}>
              <BouquetHero />
            </Suspense>
          </SceneErrorBoundary>
        </div>
      </div>
    </FadeIn>
  );
}