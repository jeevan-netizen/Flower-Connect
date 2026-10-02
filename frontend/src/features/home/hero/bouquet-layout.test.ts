import { describe, expect, it } from "vitest";
import {
  BOUQUET_DRAW_CALLS,
  BOUQUET_FLOWERS,
  BOUQUET_LEAVES,
  BOUQUET_TRIANGLES,
  BUDGET,
  GEOMETRY,
  SCENE_BOUNDS,
  STEM_BASE,
  WRAP,
  stemLength,
  withinSceneBounds,
} from "./bouquet-layout";

/**
 * The scene's cost is a design constraint, not an incidental measurement, so it
 * is asserted from the layout constants. No canvas and no WebGL context needed:
 * the numbers are derived from the same geometry parameters the scene builds
 * with.
 */
describe("bouquet geometry budget", () => {
  it("stays well inside the triangle ceiling", () => {
    expect(BOUQUET_TRIANGLES).toBeGreaterThan(0);
    expect(BOUQUET_TRIANGLES).toBeLessThan(BUDGET.maxTriangles);
  });

  it("keeps draw calls independent of the flower count", () => {
    // One instanced stem mesh, one instanced head mesh (per-instance colour),
    // one instanced leaf mesh, one wrap.
    expect(BOUQUET_DRAW_CALLS).toBe(4);
    expect(BOUQUET_DRAW_CALLS).toBeLessThanOrEqual(BUDGET.maxDrawCalls);
  });

  it("uses low-poly primitives", () => {
    expect(GEOMETRY.stemRadialSegments).toBeLessThanOrEqual(8);
    expect(GEOMETRY.headDetail).toBeLessThanOrEqual(1);
    expect(GEOMETRY.wrapRadialSegments).toBeLessThanOrEqual(12);
  });

  it("places every stem, head and leaf inside the camera framing", () => {
    expect(withinSceneBounds()).toBe(true);
    expect(SCENE_BOUNDS.maxX).toBeGreaterThan(SCENE_BOUNDS.minX);
  });

  it("converges every stem into the wrap", () => {
    for (const flower of BOUQUET_FLOWERS) {
      expect(stemLength(flower)).toBeGreaterThan(0.2);
      expect(stemLength(flower)).toBeLessThan(2);
    }
    // The wrap mouth sits below every head, so no stem points downwards.
    const wrapTop = WRAP.position[1] + WRAP.height / 2;
    expect(wrapTop).toBeLessThan(Math.min(...BOUQUET_FLOWERS.map((f) => f.head[1])));
    expect(STEM_BASE[1]).toBeGreaterThanOrEqual(wrapTop - 0.1);
  });

  it("gives every flower a colour and a positive scale", () => {
    for (const flower of BOUQUET_FLOWERS) {
      expect(flower.color).toMatch(/^#[0-9a-f]{6}$/i);
      expect(flower.scale).toBeGreaterThan(0);
      expect(flower.head.every((value) => Number.isFinite(value))).toBe(true);
    }
    for (const leaf of BOUQUET_LEAVES) {
      expect(leaf.position.every((value) => Number.isFinite(value))).toBe(true);
      expect(leaf.rotation.every((value) => Number.isFinite(value))).toBe(true);
    }
  });
});