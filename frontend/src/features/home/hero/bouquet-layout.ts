/**
 * Layout and budget data for the procedural bouquet.
 *
 * This module is deliberately free of React and of any runtime Three.js import
 * (the geometry types are structural, not `THREE.Vector3`), so the budget in
 * `BOUQUET_TRIANGLES` / `BOUQUET_DRAW_CALLS` can be asserted in a plain jsdom
 * test with no WebGL context and no canvas.
 *
 * The scene is entirely procedural: there is no `.glb`, no `.gltf`, no texture
 * and no downloaded asset anywhere in this feature.
 */

export type Vec3 = readonly [number, number, number];

export interface FlowerPlacement {
  /** Flower head centre, in scene units. */
  readonly head: Vec3;
  readonly color: string;
  /** Uniform multiplier on the shared head geometry. */
  readonly scale: number;
}

export interface LeafPlacement {
  readonly position: Vec3;
  readonly rotation: Vec3;
}

/** Every stem converges into the wrap, just above its mouth. */
export const STEM_BASE: Vec3 = [0, -0.33, 0];

/** The paper wrap the stems are gathered into. */
export const WRAP = {
  position: [0, -0.58, 0] as Vec3,
  topRadius: 0.34,
  bottomRadius: 0.12,
  height: 0.55,
};

/**
 * Seven heads in a shallow fan. The palette is drawn from the Tailwind scale the
 * rest of the app uses; nothing here is a brand-critical colour, so no
 * Tailwind change is needed to render it.
 */
export const BOUQUET_FLOWERS: readonly FlowerPlacement[] = [
  { head: [-0.52, 0.1, 0.1], color: "#f472b6", scale: 1 },
  { head: [-0.3, 0.34, 0.02], color: "#fbbf24", scale: 1.15 },
  { head: [-0.06, 0.46, -0.05], color: "#f8fafc", scale: 1.25 },
  { head: [0.2, 0.36, 0.04], color: "#c4b5fd", scale: 1.1 },
  { head: [0.44, 0.12, 0.1], color: "#fb7185", scale: 1 },
  { head: [-0.14, 0.02, 0.22], color: "#fde68a", scale: 0.95 },
  { head: [0.1, 0.06, -0.2], color: "#f9a8d4", scale: 0.9 },
];

export const BOUQUET_LEAVES: readonly LeafPlacement[] = [
  { position: [-0.4, -0.06, 0.16], rotation: [0, 0.4, -0.9] },
  { position: [0.34, -0.1, 0.16], rotation: [0, -0.4, 0.9] },
  { position: [-0.2, -0.18, -0.18], rotation: [0, 0.9, -0.6] },
  { position: [0.22, -0.22, -0.16], rotation: [0, -0.9, 0.6] },
  { position: [-0.02, -0.26, 0.2], rotation: [0.2, 0.3, 0.2] },
  { position: [0.06, -0.14, -0.24], rotation: [-0.2, -0.3, -0.2] },
  { position: [-0.52, 0.22, -0.06], rotation: [0, 0.2, -1.1] },
];

/**
 * Shared geometry resolution. One segment-heavy primitive would blow the budget;
 * flat-shaded low-poly primitives keep the silhouette readable at ~700
 * triangles total.
 */
export const GEOMETRY = {
  /** Radial segments of the open-ended stem cylinder. */
  stemRadialSegments: 6,
  /** Icosahedron subdivision: `20 * (detail + 1)^2` faces per head. */
  headDetail: 1,
  /** Radial segments of the wrap cone. */
  wrapRadialSegments: 8,
} as const;

/**
 * Draw calls: one instanced stem mesh, one instanced head mesh (per-instance
 * colour, so one material), one instanced leaf mesh, one wrap. Four in total,
 * regardless of how many flowers there are.
 */
export const BOUQUET_DRAW_CALLS = 4;

/** Open-ended cylinder: one quad per radial segment. */
const STEM_TRIANGLES = GEOMETRY.stemRadialSegments * 2;
const HEAD_TRIANGLES = 20 * (GEOMETRY.headDetail + 1) ** 2;
const LEAF_TRIANGLES = 2;
const WRAP_TRIANGLES = GEOMETRY.wrapRadialSegments * 2;

export const BOUQUET_TRIANGLES =
  BOUQUET_FLOWERS.length * (STEM_TRIANGLES + HEAD_TRIANGLES) +
  BOUQUET_LEAVES.length * LEAF_TRIANGLES +
  WRAP_TRIANGLES;

/** Hard ceilings the design is held to; asserted by `bouquet-layout.test.ts`. */
export const BUDGET = {
  maxTriangles: 8000,
  maxDrawCalls: 8,
} as const;

/** The camera framing the scene is built for; used to check nothing escapes it. */
export const SCENE_BOUNDS = {
  minX: -0.75,
  maxX: 0.75,
  minY: -0.75,
  maxY: 0.75,
} as const;

export function stemLength(flower: FlowerPlacement): number {
  const dx = flower.head[0] - STEM_BASE[0];
  const dy = flower.head[1] - STEM_BASE[1];
  const dz = flower.head[2] - STEM_BASE[2];
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

/** True when no flower head or leaf sits outside the camera framing. */
export function withinSceneBounds(): boolean {
  const inside = (value: Vec3) =>
    value[0] >= SCENE_BOUNDS.minX &&
    value[0] <= SCENE_BOUNDS.maxX &&
    value[1] >= SCENE_BOUNDS.minY &&
    value[1] <= SCENE_BOUNDS.maxY;

  return (
    BOUQUET_FLOWERS.every((flower) => inside(flower.head)) &&
    BOUQUET_LEAVES.every((leaf) => inside(leaf.position))
  );
}