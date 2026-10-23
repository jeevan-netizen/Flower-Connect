import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { usePolling } from "@/hooks/usePolling";

describe("usePolling", () => {
  let callback: ReturnType<typeof vi.fn>;
  let interval: number;

  beforeEach(() => {
    vi.useFakeTimers();
    callback = vi.fn();
    interval = 8_000;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("invokes the callback every interval while enabled and visible", () => {
    renderHook(() => usePolling(callback, { interval, enabled: true }));
    expect(callback).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).toHaveBeenCalledTimes(1);

    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).toHaveBeenCalledTimes(2);
  });

  it("does not poll when disabled", () => {
    renderHook(() => usePolling(callback, { interval, enabled: false }));
    act(() => {
      vi.advanceTimersByTime(interval * 3);
    });
    expect(callback).not.toHaveBeenCalled();
  });

  it("does not poll while the tab is hidden", () => {
    Object.defineProperty(document, "hidden", { value: true, configurable: true });
    renderHook(() => usePolling(callback, { interval, enabled: true }));
    act(() => {
      vi.advanceTimersByTime(interval * 3);
    });
    expect(callback).not.toHaveBeenCalled();
  });

  it("polls once when the tab becomes visible again", () => {
    Object.defineProperty(document, "hidden", { value: true, configurable: true });
    renderHook(() => usePolling(callback, { interval, enabled: true }));
    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).not.toHaveBeenCalled();

    Object.defineProperty(document, "hidden", { value: false, configurable: true });
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("stops polling and removes the listener on unmount", () => {
    const { unmount } = renderHook(() => usePolling(callback, { interval, enabled: true }));
    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).toHaveBeenCalledTimes(1);

    unmount();
    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).toHaveBeenCalledTimes(1);
  });

  it("restarts polling when re-enabled", () => {
    const { rerender } = renderHook(
      ({ enabled }) => usePolling(callback, { interval, enabled }),
      { initialProps: { enabled: false } },
    );
    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).not.toHaveBeenCalled();

    rerender({ enabled: true });
    act(() => {
      vi.advanceTimersByTime(interval);
    });
    expect(callback).toHaveBeenCalledTimes(1);
  });
});
