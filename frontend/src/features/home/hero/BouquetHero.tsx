import { useCallback, useEffect, useRef, useState } from "react";
import { HERO_FRAME_CLASS, HeroFallback } from "./HeroFallback";
import { SceneErrorBoundary } from "./SceneErrorBoundary";
import { BouquetScene } from "./BouquetScene";
import { useHeroInteractionLevel } from "./useHeroInteractionLevel";
import { useSceneActivity } from "./useSceneActivity";
import { useWebGLAvailable } from "./useWebGLAvailable";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { durations } from "@/motion/tokens";

/**
 * How long to wait before rebuilding a canvas after its context was lost.
 *
 * A GPU driver reset or a laptop switching graphics cards restores the context
 * asynchronously, so the fallback is shown immediately and the canvas is retried
 * once, this many milliseconds later. Expressed against the page-entry motion
 * token rather than as a bare literal, per the no-hardcoded-motion-values rule.
 */
const CONTEXT_RETRY_MS = durations.page * 1000 * 3;

export interface BouquetHeroProps {
  className?: string;
}

/**
 * Everything that must be true before a `Canvas` exists, in order:
 * WebGL support, no lost context, then an error boundary around the scene.
 *
 * Default export because `HomePage` loads it with `React.lazy`, which keeps
 * Three.js and R3F out of the initial chunk.
 */
export default function BouquetHero({ className }: BouquetHeroProps) {
  const webglAvailable = useWebGLAvailable();
  const interaction = useHeroInteractionLevel();
  const reducedMotion = usePrefersReducedMotion();

  const [contextLost, setContextLost] = useState(false);
  const [attempt, setAttempt] = useState(0);

  const frameRef = useRef<HTMLDivElement>(null);
  // `attempt` is the remount key: it changes exactly when the frame node is
  // replaced by a context retry, which is when the observer has to move.
  const { inView, documentVisible } = useSceneActivity(frameRef, attempt);

  useEffect(() => {
    // Exactly one retry, which is what the comment on CONTEXT_RETRY_MS promises.
    // Without the `attempt > 0` guard a device that loses every context it is
    // handed — a crashing or blocklisted GPU driver — would re-enter this loop
    // about once a second for the life of the page, building and dropping a
    // WebGL context each time.
    if (!contextLost || attempt > 0) {
      return;
    }
    const timer = window.setTimeout(() => {
      setContextLost(false);
      // Remounting gives the retry a brand new canvas, and therefore a context
      // that has not already failed.
      setAttempt((value) => value + 1);
    }, CONTEXT_RETRY_MS);
    return () => window.clearTimeout(timer);
  }, [contextLost, attempt]);

  // Without this the boundary swallows scene failures silently, and a user
  // staring at a permanently static hero leaves no trace to diagnose.
  const handleSceneError = useCallback((error: Error) => {
    console.error("[hero] 3D bouquet scene failed to render; showing the static fallback", error);
  }, []);

  const frameClass = className ?? HERO_FRAME_CLASS;

  if (!webglAvailable || contextLost) {
    return <HeroFallback className={frameClass} />;
  }

  // A static scene is rendered once and then left alone; an animated one only
  // runs its loop while it is both on screen and in a visible document.
  const staticMode = reducedMotion || interaction === "none";
  const frameloop = !inView || !documentVisible ? "never" : staticMode ? "demand" : "always";

  return (
    <SceneErrorBoundary
      fallback={<HeroFallback className={frameClass} />}
      onError={handleSceneError}
    >
      <div
        ref={frameRef}
        data-testid="hero-canvas-frame"
        className={`${frameClass} ${
          // No pointer interaction means no pointer events either: a canvas that
          // never reacts to input must not intercept taps or block scrolling.
          interaction === "none" ? "pointer-events-none" : "pointer-events-auto"
        }`}
      >
        <BouquetScene
          key={attempt}
          interaction={interaction}
          staticMode={staticMode}
          frameloop={frameloop}
          onContextLost={() => setContextLost(true)}
          onContextRestored={() => setContextLost(false)}
        />
      </div>
    </SceneErrorBoundary>
  );
}