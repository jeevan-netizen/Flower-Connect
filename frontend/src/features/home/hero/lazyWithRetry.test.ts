import { afterEach, describe, expect, it, vi } from "vitest";
import { lazyWithRetry } from "./lazyWithRetry";

/**
 * The retry exists for one situation: the first request for the chunk failed for
 * a reason that will not repeat (a dropped connection, a throttled request). It
 * must not become a request loop for an asset that is genuinely absent.
 */

const FLAG = "test-hero-retry-flag";

afterEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe("lazyWithRetry", () => {
  it("resolves without retrying when the first load succeeds", async () => {
    const module = { default: "hero" };
    const loader = vi.fn().mockResolvedValue(module);

    await expect(lazyWithRetry(loader, FLAG)).resolves.toBe(module);
    expect(loader).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(FLAG)).toBeNull();
  });

  it("retries once and resolves when the first load rejects", async () => {
    const module = { default: "hero" };
    const loader = vi
      .fn()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(module);

    await expect(lazyWithRetry(loader, FLAG)).resolves.toBe(module);
    expect(loader).toHaveBeenCalledTimes(2);
    expect(sessionStorage.getItem(FLAG)).toBe("1");
  });

  it("rethrows on the retry too, and does not retry a third time", async () => {
    const loader = vi.fn().mockRejectedValue(new Error("missing chunk"));

    await expect(lazyWithRetry(loader, FLAG)).rejects.toThrow("missing chunk");
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it("skips the retry entirely when the flag is already set", async () => {
    sessionStorage.setItem(FLAG, "1");
    const loader = vi.fn().mockRejectedValue(new Error("missing chunk"));

    await expect(lazyWithRetry(loader, FLAG)).rejects.toThrow("missing chunk");
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it("still retries when the storage write fails", async () => {
    const module = { default: "hero" };
    const loader = vi
      .fn()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(module);
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("QuotaExceededError");
    });

    await expect(lazyWithRetry(loader, FLAG)).resolves.toBe(module);
    expect(loader).toHaveBeenCalledTimes(2);
  });
});