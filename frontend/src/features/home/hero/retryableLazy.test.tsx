import { Suspense } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { SceneErrorBoundary } from "./SceneErrorBoundary";
import { createRetryableLazy } from "./lazyWithRetry";

/**
 * Regression cover for the lazy-chunk recovery gap found in production
 * hardening: `lazyWithRetry` retries once, but `React.lazy` caches a *rejected*
 * payload and re-throws it forever. An attempt hoisted to module scope therefore
 * stays dead after its retry fails — navigating away and back re-renders the
 * same rejected component instead of asking for the chunk again.
 *
 * `createRetryableLazy` fixes it by building the attempt per visit, so each
 * visit arrives with an empty payload cache and its own one-shot retry budget.
 * These tests drive that exact sequence: fail, fail on the retry, leave, return,
 * recover.
 */

const FLAG = "test-hero-retry-flag";

function HeroStub() {
  return <p>3D hero mounted</p>;
}

function renderAttempt(Component: React.ComponentType) {
  return render(
    <SceneErrorBoundary fallback={<p>static fallback</p>} onError={() => {}}>
      <Suspense fallback={<p>loading hero</p>}>
        <Component />
      </Suspense>
    </SceneErrorBoundary>,
  );
}

afterEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe("createRetryableLazy", () => {
  it("recovers on the next visit after the first attempt and its retry both failed", async () => {
    // React logs the caught error through console.error; the boundary handles it.
    vi.spyOn(console, "error").mockImplementation(() => {});
    const failing = vi.fn(() => Promise.reject(new Error("chunk fetch failed")));

    const firstVisit = createRetryableLazy(failing, FLAG);
    const { unmount } = renderAttempt(firstVisit.Component);

    await waitFor(() => expect(screen.getByText("static fallback")).toBeInTheDocument());
    // Initial request plus the one retry `lazyWithRetry` allows, then it stops.
    expect(failing).toHaveBeenCalledTimes(2);
    expect(sessionStorage.getItem(FLAG)).toBe("1");
    unmount();

    // Coming back is a new attempt, so the chunk is requested afresh instead of
    // the rejected payload being replayed.
    const recovered = vi.fn().mockResolvedValue({ default: HeroStub });
    renderAttempt(createRetryableLazy(recovered, FLAG).Component);

    expect(await screen.findByText("3D hero mounted")).toBeInTheDocument();
    expect(recovered).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(FLAG)).toBeNull();
  });

  it("still has its one-shot retry available on the next visit", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    // Fails every request until the third: visit one spends two, visit two's
    // first attempt is the third and fails, so the retry must carry it.
    let requests = 0;
    const flaky = vi.fn(() => {
      requests += 1;
      return requests <= 2
        ? Promise.reject(new Error("network"))
        : Promise.resolve({ default: HeroStub });
    });

    const firstVisit = createRetryableLazy(flaky, FLAG);
    const { unmount } = renderAttempt(firstVisit.Component);
    await waitFor(() => expect(screen.getByText("static fallback")).toBeInTheDocument());
    expect(requests).toBe(2);
    unmount();

    renderAttempt(createRetryableLazy(flaky, FLAG).Component);

    expect(await screen.findByText("3D hero mounted")).toBeInTheDocument();
    expect(requests).toBe(3);
  });

  it("keeps a spent attempt spent, which is why a caller must rebuild it per visit", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const failing = vi.fn(() => Promise.reject(new Error("chunk fetch failed")));

    const visit = createRetryableLazy(failing, FLAG);
    renderAttempt(visit.Component);
    await waitFor(() => expect(screen.getByText("static fallback")).toBeInTheDocument());
    expect(failing).toHaveBeenCalledTimes(2);

    // Re-rendering the same component object replays the cached rejection: no
    // further request, and no recovery. This is the behaviour the per-visit
    // factory exists to avoid, pinned so it cannot be reintroduced silently.
    renderAttempt(visit.Component);

    await waitFor(() => expect(screen.getAllByText("static fallback")).toHaveLength(2));
    expect(failing).toHaveBeenCalledTimes(2);
  });
});
