import { BLOOM_COLORS, SCENE_COLORS } from "./bouquet-palette";

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
 *
 * Colours come from `bouquet-palette`, which mirrors the landing design tokens.
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

/** The paper wrap the stems are gathered into, and the trim around its mouth. */
export const WRAP = {
  position: [0, -0.58, 0] as Vec3,
  topRadius: 0.34,
  bottomRadius: 0.12,
  height: 0.55,
  /** Half-height of the cone: where the mouth, and therefore the trim, sits. */
  halfHeight: 0.275,
  /** Trim ring, a shade wider than the mouth so it reads as a separate fold. */
  rimRadius: 0.355,
  rimHeight: 0.05,
} as const;

/**
 * Eighteen heads in three depth layers, which is what makes the bouquet read as
 * full rather than as a flat fan:
 *
 * - a **back** layer of deep plum and rose at `z < 0`, which fills the
 *   silhouette between the front blooms and gives the cluster depth;
 * - a **mid** fan carrying the plum / pink / rose / gold palette the design
 *   asks for, with the largest heads on the outer shoulders;
 * - a **front** layer of small blush and gold fillers that overlap the mid fan
 *   and hide where the stems meet the wrap.
 *
 * Every head sits above the wrap mouth, so no stem points downwards.
 */
export const BOUQUET_FLOWERS: readonly FlowerPlacement[] = [
  // Back layer: silhouette and depth.
  { head: [-0.38, 0.18, -0.22], color: BLOOM_COLORS.plum, scale: 0.85 },
  { head: [-0.11, 0.3, -0.28], color: BLOOM_COLORS.roseDeep, scale: 0.8 },
  { head: [0.15, 0.26, -0.26], color: BLOOM_COLORS.plum, scale: 0.75 },
  { head: [0.38, 0.2, -0.2], color: BLOOM_COLORS.roseDeep, scale: 0.85 },
  { head: [-0.46, 0.02, -0.14], color: BLOOM_COLORS.plum, scale: 0.7 },
  { head: [0.46, 0.06, -0.12], color: BLOOM_COLORS.plum, scale: 0.7 },

  // Mid fan: the palette the design specifies.
  { head: [-0.42, 0.3, -0.02], color: BLOOM_COLORS.roseStrong, scale: 1 },
  { head: [-0.24, 0.42, 0.02], color: BLOOM_COLORS.blush, scale: 1.1 },
  { head: [-0.05, 0.5, -0.04], color: BLOOM_COLORS.gold, scale: 1 },
  { head: [0.16, 0.44, 0.04], color: BLOOM_COLORS.rose, scale: 1.05 },
  { head: [0.34, 0.32, -0.02], color: BLOOM_COLORS.blushLight, scale: 0.95 },
  { head: [0.5, 0.14, 0], color: BLOOM_COLORS.roseStrong, scale: 0.85 },
  { head: [-0.5, 0.14, 0.02], color: BLOOM_COLORS.rose, scale: 0.85 },

  // Front fillers: overlap the mid fan and cover the stem junction.
  { head: [-0.2, 0.16, 0.22], color: BLOOM_COLORS.blushLight, scale: 0.9 },
  { head: [0.02, 0.08, 0.26], color: BLOOM_COLORS.gold, scale: 0.85 },
  { head: [0.22, 0.18, 0.22], color: BLOOM_COLORS.blush, scale: 0.9 },
  { head: [-0.32, 0.04, 0.24], color: BLOOM_COLORS.rose, scale: 0.8 },
  { head: [0.34, 0.0, 0.18], color: BLOOM_COLORS.blushLight, scale: 0.8 },
];

/**
 * Twenty-seven leaves in three bands: a low skirt around the wrap, a mid band
 * threaded between the heads, and a few small fillers along the top edge. The
 * bands matter — foliage in one ring only reads as a wreath, not as a bouquet.
 */
export const BOUQUET_LEAVES: readonly LeafPlacement[] = [
  // Low skirt around the wrap.
  { position: [-0.34, -0.06, 0.14], rotation: [0, 0.5, -1] },
  { position: [0.3, -0.08, 0.16], rotation: [0, -0.5, 1] },
  { position: [-0.16, -0.14, 0.2], rotation: [0.2, 0.3, 0.25] },
  { position: [0.18, -0.16, 0.18], rotation: [0.2, -0.3, -0.25] },
  { position: [-0.42, -0.16, -0.1], rotation: [0, -0.8, -0.7] },
  { position: [0.4, -0.18, -0.08], rotation: [0, 0.8, 0.7] },
  { position: [-0.06, -0.2, -0.22], rotation: [-0.2, 0.2, -0.2] },
  { position: [0.08, -0.22, -0.2], rotation: [-0.2, -0.2, 0.2] },
  { position: [-0.5, -0.02, 0.06], rotation: [0, 0.4, -1.15] },
  { position: [0.48, -0.04, 0.06], rotation: [0, -0.4, 1.15] },

  // Mid band, threaded between the heads.
  { position: [-0.3, 0.1, 0.16], rotation: [0.15, 0.6, -0.85] },
  { position: [0.28, 0.12, 0.16], rotation: [0.15, -0.6, 0.85] },
  { position: [-0.14, 0.22, -0.2], rotation: [0, -0.9, -0.6] },
  { position: [0.16, 0.2, -0.22], rotation: [0, 0.9, 0.6] },
  { position: [-0.44, 0.24, 0.1], rotation: [0, 0.2, -1.05] },
  { position: [0.44, 0.26, 0.08], rotation: [0, -0.2, 1.05] },
  { position: [-0.2, 0.34, 0.14], rotation: [0.1, 0.5, -0.7] },
  { position: [0.24, 0.36, 0.12], rotation: [0.1, -0.5, 0.7] },
  { position: [0.0, 0.28, 0.22], rotation: [0.25, 0.2, 0.15] },
  { position: [-0.34, 0.34, -0.14], rotation: [0, -0.3, -0.9] },
  { position: [0.36, 0.32, -0.16], rotation: [0, 0.3, 0.9] },

  // Top-edge fillers, so the fan has no bald shoulders.
  { position: [-0.34, 0.44, -0.18], rotation: [0, 0.1, -1.2] },
  { position: [0.32, 0.46, -0.16], rotation: [0, -0.1, 1.2] },
  { position: [-0.1, 0.52, 0.14], rotation: [0.15, 0.3, -0.5] },
  { position: [0.12, 0.5, 0.16], rotation: [0.15, -0.3, 0.5] },
  { position: [-0.58, 0.18, -0.06], rotation: [0, 0.15, -1.25] },
  { position: [0.56, 0.2, -0.04], rotation: [0, -0.15, 1.25] },
];

/**
 * Shared geometry resolution. One segment-heavy primitive would blow the budget;
 * flat-shaded low-poly primitives keep the silhouette readable at well under
 * 2000 triangles in total.
 */
export const GEOMETRY = {
  /** Radial segments of the open-ended stem cylinder. */
  stemRadialSegments: 6,
  /** Icosahedron subdivision: `20 * (detail + 1)^2` faces per head. */
  headDetail: 1,
  /** Radial segments of the wrap cone and of the trim ring. */
  wrapRadialSegments: 8,
} as const;

/**
 * Draw calls: one instanced stem mesh, one instanced head mesh (per-instance
 * colour, so one material), one instanced leaf mesh, the wrap cone and its trim
 * ring. Five in total, regardless of how many flowers there are.
 */
export const BOUQUET_DRAW_CALLS = 5;

/** Open-ended cylinder: one quad per radial segment. */
const STEM_TRIANGLES = GEOMETRY.stemRadialSegments * 2;
const HEAD_TRIANGLES = 20 * (GEOMETRY.headDetail + 1) ** 2;
const LEAF_TRIANGLES = 2;
const WRAP_TRIANGLES = GEOMETRY.wrapRadialSegments * 2;

export const BOUQUET_TRIANGLES =
  BOUQUET_FLOWERS.length * (STEM_TRIANGLES + HEAD_TRIANGLES) +
  BOUQUET_LEAVES.length * LEAF_TRIANGLES +
  // The cone and its trim ring.
  WRAP_TRIANGLES * 2;

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

/** Materials, named by the palette token they mirror. */
export const WRAP_COLOR = SCENE_COLORS.wrap;
export const WRAP_TRIM_COLOR = SCENE_COLORS.wrapTrim;
export const STEM_COLOR = SCENE_COLORS.stem;
export const LEAF_COLOR = SCENE_COLORS.leaf;

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
