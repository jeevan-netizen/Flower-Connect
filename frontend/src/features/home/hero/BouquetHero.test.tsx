import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import type { BouquetSceneProps } from "./BouquetScene";
import BouquetHero from "./BouquetHero";

/**
 * The real `Canvas` needs WebGL, which jsdom does not have. `BouquetScene` is
 * therefore replaced by a stub that records the props it was handed — the props
 * are the contract `BouquetHero` owns (render loop, static mode, interaction
 * level, context-loss callbacks), and the actual WebGL drawing is asserted by the
 * browser pass, not here.
 */
const scene = vi.hoisted(() => ({
  rendered: [] as unknown[],
  shouldThrow: false,
}));

vi.mock("@/features/home/hero/BouquetScene", () => ({
  BouquetScene: (props: unknown) => {
    if (scene.shouldThrow) {
      throw new Error("context creation failed");
    }
    scene.rendered.push(props);
    return <div data-testid="mock-scene" />;
  },
}));

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const DESKTOP_QUERY = "(min-width: 768px) and (hover: hover) and (pointer: fine)";

function stubMedia(matchingQueries: readonly string[]) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: matchingQueries.includes(query),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }));
}

function enableWebGL() {
  vi.stubGlobal("WebGLRenderingContext", class WebGLRenderingContext {});
  vi.stubGlobal("WebGL2RenderingContext", class WebGL2RenderingContext {});
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    // The probe releases its context through `WEBGL_lose_context`, so the stub
    // has to answer `getExtension` the way a real context does.
    getExtension: () => null,
  } as never);
}

function disableWebGL() {
  vi.stubGlobal("WebGLRenderingContext", undefined);
  vi.stubGlobal("WebGL2RenderingContext", undefined);
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null as never);
}

function lastSceneProps(): BouquetSceneProps {
  return scene.rendered[scene.rendered.length - 1] as BouquetSceneProps;
}

beforeEach(() => {
  scene.rendered = [];
  scene.shouldThrow = false;
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("BouquetHero", () => {
  it("renders the static fallback and mounts no canvas when WebGL is unavailable", () => {
    disableWebGL();
    stubMedia([REDUCED_MOTION_QUERY]);

    render(<BouquetHero />);

    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
    expect(screen.queryByTestId("hero-canvas-frame")).not.toBeInTheDocument();
    expect(scene.rendered).toHaveLength(0);
  });

  it("falls back instead of crashing when the scene throws", () => {
    enableWebGL();
    stubMedia([REDUCED_MOTION_QUERY]);
    scene.shouldThrow = true;
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<BouquetHero />);

    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
    expect(screen.queryByTestId("mock-scene")).not.toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("renders a static scene when motion is reduced", () => {
    enableWebGL();
    stubMedia([REDUCED_MOTION_QUERY, DESKTOP_QUERY]);

    render(<BouquetHero />);

    expect(screen.getByTestId("mock-scene")).toBeInTheDocument();
    expect(lastSceneProps().staticMode).toBe(true);
    // `demand` renders the first frame and then stops requesting frames.
    expect(lastSceneProps().frameloop).toBe("demand");
    expect(screen.queryByTestId("hero-fallback")).not.toBeInTheDocument();
  });

  it("animates a visible, interactive scene on desktop", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);

    render(<BouquetHero />);

    expect(lastSceneProps().staticMode).toBe(false);
    expect(lastSceneProps().interaction).toBe("full");
    expect(lastSceneProps().frameloop).toBe("always");
    expect(screen.getByTestId("hero-canvas-frame").className).toContain("pointer-events-auto");
  });

  it("renders statically and ignores the pointer on mobile", () => {
    enableWebGL();
    stubMedia([]);

    render(<BouquetHero />);

    expect(lastSceneProps().interaction).toBe("none");
    expect(lastSceneProps().staticMode).toBe(true);
    expect(lastSceneProps().frameloop).toBe("demand");
    expect(screen.getByTestId("hero-canvas-frame").className).toContain("pointer-events-none");
  });

  it("shows the fallback and retries once the canvas reports a lost context", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);
    vi.useFakeTimers();

    render(<BouquetHero />);
    expect(scene.rendered).toHaveLength(1);

    act(() => lastSceneProps().onContextLost());

    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
    expect(screen.queryByTestId("mock-scene")).not.toBeInTheDocument();

    act(() => {
      vi.runAllTimers();
    });

    // The retry remounts a fresh canvas rather than leaving the hero degraded.
    expect(screen.getByTestId("mock-scene")).toBeInTheDocument();
    expect(scene.rendered).toHaveLength(2);
    vi.useRealTimers();
  });

  it("retries a lost context only once, so a permanently failing GPU cannot spin the loop", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);
    vi.useFakeTimers();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<BouquetHero />);
    act(() => lastSceneProps().onContextLost());
    act(() => {
      vi.runAllTimers();
    });
    expect(scene.rendered).toHaveLength(2);

    // A second loss must not schedule another retry.
    act(() => lastSceneProps().onContextLost());
    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
    act(() => {
      vi.runAllTimers();
    });
    expect(scene.rendered).toHaveLength(2);
    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();

    consoleError.mockRestore();
    vi.useRealTimers();
  });

  it("re-attaches the visibility observer to the frame rebuilt by a context retry", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);
    vi.useFakeTimers();

    const observed: Element[] = [];
    class FakeIntersectionObserver {
      observe(element: Element): void {
        observed.push(element);
      }
      unobserve(): void {}
      disconnect(): void {}
      takeRecords(): IntersectionObserverEntry[] {
        return [];
      }
    }
    vi.stubGlobal(
      "IntersectionObserver",
      FakeIntersectionObserver as unknown as typeof IntersectionObserver,
    );

    render(<BouquetHero />);
    const firstFrame = screen.getByTestId("hero-canvas-frame");
    expect(observed).toEqual([firstFrame]);

    act(() => lastSceneProps().onContextLost());
    act(() => {
      vi.runAllTimers();
    });

    // The retry mounts a new frame; the observer must follow it rather than
    // stay bound to the node the fallback tore out of the DOM.
    const retriedFrame = screen.getByTestId("hero-canvas-frame");
    expect(retriedFrame).not.toBe(firstFrame);
    expect(observed).toEqual([firstFrame, retriedFrame]);
    vi.useRealTimers();
  });

  it("stops the render loop while the document is hidden", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);
    Object.defineProperty(document, "hidden", { configurable: true, value: true });

    try {
      render(<BouquetHero />);
      expect(lastSceneProps().frameloop).toBe("never");

      Object.defineProperty(document, "hidden", { configurable: true, value: false });
      act(() => {
        document.dispatchEvent(new Event("visibilitychange"));
      });
      expect(lastSceneProps().frameloop).toBe("always");
    } finally {
      Object.defineProperty(document, "hidden", { configurable: true, value: false });
    }
  });

  it("stops the render loop while the hero is scrolled out of view", () => {
    enableWebGL();
    stubMedia([DESKTOP_QUERY]);

    const instances: IntersectionObserverCallback[] = [];
    class FakeIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        instances.push(callback);
      }
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
      takeRecords(): IntersectionObserverEntry[] {
        return [];
      }
    }
    vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver as unknown as typeof IntersectionObserver);

    render(<BouquetHero />);
    expect(lastSceneProps().frameloop).toBe("always");

    act(() => {
      instances[0]?.(
        [{ isIntersecting: false } as IntersectionObserverEntry],
        {} as IntersectionObserver,
      );
    });

    expect(lastSceneProps().frameloop).toBe("never");
  });
});