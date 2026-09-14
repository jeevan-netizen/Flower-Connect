import { expect, describe, it } from "vitest";

describe("smoke", () => {
  it("renders placeholder", () => {
    const el = document.createElement("div");
    el.className = "fc";
    expect(el.className).toBe("fc");
  });
});