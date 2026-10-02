import { useState } from "react";

/**
 * Probes for a usable WebGL context without throwing.
 *
 * The `WebGLRenderingContext` constructor check comes first on purpose: in jsdom
 * (and in any environment without WebGL) `canvas.getContext("webgl")` does not
 * return `null`, it raises a "not implemented" error through the virtual
 * console, which would spam test output and tell us nothing. Bail out before
 * touching the canvas in that case.
 *
 * The probe result is decided once per mount: the answer cannot change without a
 * page reload in any browser we target, and re-probing on every render would
 * allocate a canvas per render.
 */
export function detectWebGL(): boolean {
  if (typeof window === "undefined" || typeof document === "undefined") {
    return false;
  }

  if (
    typeof window.WebGLRenderingContext === "undefined" &&
    typeof window.WebGL2RenderingContext === "undefined"
  ) {
    return false;
  }

  try {
    const canvas = document.createElement("canvas");
    // `experimental-webgl` is typed as the wider `RenderingContext`, which has no
    // `getExtension`; every WebGL flavour does, so the union is narrowed here.
    const context =
      (canvas.getContext("webgl2") as WebGL2RenderingContext | null) ??
      (canvas.getContext("webgl") as WebGLRenderingContext | null) ??
      (canvas.getContext("experimental-webgl") as WebGLRenderingContext | null);
    if (!context) {
      return false;
    }
    // Hand the context straight back. Browsers cap how many live WebGL contexts
    // a page may hold (low on mobile), and a probe that kept its slot for the
    // life of the page could push the real canvas into an eviction it would
    // only learn about as a `webglcontextlost` event.
    context.getExtension("WEBGL_lose_context")?.loseContext();
    return true;
  } catch {
    return false;
  }
}

/**
 * Whether a `Canvas` may be mounted at all. Checked before the scene is created
 * so an unsupported browser never reaches WebGL, never logs a Three.js warning,
 * and renders `HeroFallback` instead.
 */
export function useWebGLAvailable(): boolean {
  const [supported] = useState(detectWebGL);
  return supported;
}