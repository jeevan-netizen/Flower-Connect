import { useEffect, useState, type RefObject } from "react";

export interface SceneActivity {
  /** The hero frame intersects the viewport. */
  inView: boolean;
  /** The document is not in a background tab. */
  documentVisible: boolean;
}

/**
 * Whether the scene is worth rendering frames at all.
 *
 * A continuously animated `Canvas` keeps calling `requestAnimationFrame` even
 * when it is scrolled out of view or its tab is in the background, and the
 * browser keeps doing that work (GPU, and CPU on software renderers) for pixels
 * nobody sees. Both signals are reported here so `BouquetHero` can drop the
 * render loop to `frameloop="never"` and resume on return.
 *
 * `inView` starts `true` and stays `true` when `IntersectionObserver` is
 * unavailable: assuming "not visible" would silently disable the animation on
 * an older browser, whereas assuming "visible" only costs frames.
 *
 * The observed node does not have a stable identity: a context loss unmounts the
 * hero frame and the retry mounts a new one. An observer keyed only on a stable
 * ref would still be watching the detached original — reporting
 * `isIntersecting: false` forever and pinning the rebuilt canvas to a paused
 * render loop. `remountKey` therefore changes whenever the node is replaced, so
 * the effect re-reads the ref after the new frame has committed.
 */
export function useSceneActivity(
  target: RefObject<HTMLElement | null>,
  remountKey: unknown,
): SceneActivity {
  const [inView, setInView] = useState(true);
  const [documentVisible, setDocumentVisible] = useState(
    () => typeof document === "undefined" || !document.hidden,
  );

  useEffect(() => {
    const element = target.current;
    if (!element || typeof IntersectionObserver === "undefined") {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[entries.length - 1];
        if (entry) {
          setInView(entry.isIntersecting);
        }
      },
      // Start rendering slightly before the frame scrolls in, so the first
      // painted frame is not an empty canvas.
      { rootMargin: "64px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [target, remountKey]);

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    const handleVisibilityChange = () => setDocumentVisible(!document.hidden);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    handleVisibilityChange();
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, []);

  return { inView, documentVisible };
}