import { BLOOM_COLORS, SCENE_COLORS } from "./bouquet-palette";

/**
 * Static, dependency-free bouquet.
 *
 * Used in three situations, all of which must render something sensible rather
 * than nothing: while the lazy 3D chunk is still downloading (Suspense
 * fallback), when WebGL is unavailable, and when the scene throws or loses its
 * context. It is deliberately plain SVG — no animation, no gradients over
 * animated stops, no JS beyond static maps — so it can be imported eagerly by
 * `HomePage` without pulling Three.js into the main chunk.
 *
 * `HERO_FRAME_CLASS` fixes the frame height at every breakpoint and is shared by
 * the fallback and the canvas, so swapping one for the other cannot shift
 * layout. The frame is transparent: the rings and glows behind it belong to the
 * page, not to the bouquet, and they show through either way.
 */
export const HERO_FRAME_CLASS =
  "relative flex h-[300px] w-full items-center justify-center overflow-hidden sm:h-[420px] lg:h-[560px]";

interface Bloom {
  readonly cx: number;
  readonly cy: number;
  readonly radius: number;
  readonly petals: number;
  readonly petalColor: string;
  readonly coreColor: string;
}

/** Same plum / pink / rose / gold palette as the 3D scene, via the shared mirror. */
const BLOOMS: readonly Bloom[] = [
  { cx: 62, cy: 72, radius: 17, petals: 5, petalColor: BLOOM_COLORS.blush, coreColor: BLOOM_COLORS.roseStrong },
  { cx: 100, cy: 48, radius: 20, petals: 6, petalColor: BLOOM_COLORS.blushLight, coreColor: BLOOM_COLORS.gold },
  { cx: 138, cy: 74, radius: 16, petals: 5, petalColor: BLOOM_COLORS.rose, coreColor: BLOOM_COLORS.roseDeep },
  { cx: 80, cy: 104, radius: 13, petals: 5, petalColor: BLOOM_COLORS.rose, coreColor: BLOOM_COLORS.plum },
  { cx: 122, cy: 110, radius: 12, petals: 5, petalColor: BLOOM_COLORS.blushLight, coreColor: BLOOM_COLORS.roseStrong },
  { cx: 40, cy: 100, radius: 10, petals: 5, petalColor: BLOOM_COLORS.gold, coreColor: BLOOM_COLORS.roseDeep },
  { cx: 160, cy: 102, radius: 10, petals: 5, petalColor: BLOOM_COLORS.plum, coreColor: BLOOM_COLORS.blush },
];

const STEMS: readonly string[] = [
  "M100 152 L62 72",
  "M100 152 L100 48",
  "M100 152 L138 74",
  "M100 152 L80 104",
  "M100 152 L122 110",
  "M100 152 C 88 120 74 108 40 100",
  "M100 152 C 114 122 132 110 160 102",
];

/** Foliage pairs, drawn as ellipses on a stem rather than as separate blades. */
const LEAVES: readonly { cx: number; cy: number; rx: number; ry: number; rotate: number }[] = [
  { cx: 84, cy: 128, rx: 5, ry: 13, rotate: -40 },
  { cx: 116, cy: 130, rx: 5, ry: 13, rotate: 40 },
  { cx: 92, cy: 140, rx: 4, ry: 10, rotate: -15 },
  { cx: 110, cy: 142, rx: 4, ry: 10, rotate: 15 },
  { cx: 74, cy: 116, rx: 4, ry: 11, rotate: -65 },
  { cx: 128, cy: 118, rx: 4, ry: 11, rotate: 65 },
];

function Petal({ bloom, index }: { bloom: Bloom; index: number }) {
  const angle = (360 / bloom.petals) * index;
  return (
    <ellipse
      cx={bloom.cx}
      cy={bloom.cy - bloom.radius * 0.62}
      rx={bloom.radius * 0.42}
      ry={bloom.radius * 0.72}
      fill={bloom.petalColor}
      transform={`rotate(${angle} ${bloom.cx} ${bloom.cy})`}
    />
  );
}

export interface HeroFallbackProps {
  className?: string;
}

/**
 * `aria-hidden` because the hero carries the same message in text: the canvas
 * and this fallback are decoration, and the page's heading, copy and calls to
 * action are the accessible content. `HomePage` adds the visually-hidden
 * description of the bouquet next to the frame for the same reason.
 */
export function HeroFallback({ className }: HeroFallbackProps) {
  return (
    <div
      data-testid="hero-fallback"
      aria-hidden="true"
      className={className ?? HERO_FRAME_CLASS}
    >
      <svg viewBox="0 0 200 200" className="h-full w-full max-w-[380px]" focusable="false">
        <g stroke={SCENE_COLORS.stem} strokeWidth="3" strokeLinecap="round" fill="none">
          {STEMS.map((points) => (
            <path key={points} d={points} />
          ))}
        </g>
        <g fill={SCENE_COLORS.leaf}>
          {LEAVES.map((leaf) => (
            <ellipse
              key={`${leaf.cx}-${leaf.cy}`}
              cx={leaf.cx}
              cy={leaf.cy}
              rx={leaf.rx}
              ry={leaf.ry}
              transform={`rotate(${leaf.rotate} ${leaf.cx} ${leaf.cy})`}
            />
          ))}
        </g>
        {BLOOMS.map((bloom) => (
          <g key={`${bloom.cx}-${bloom.cy}`}>
            {Array.from({ length: bloom.petals }, (_, index) => (
              <Petal key={index} bloom={bloom} index={index} />
            ))}
            <circle cx={bloom.cx} cy={bloom.cy} r={bloom.radius * 0.4} fill={bloom.coreColor} />
          </g>
        ))}
        {/* Cone first, trim second: the cone's mouth is where the stems gather. */}
        <path d="M82 150 L118 150 L110 190 L90 190 Z" fill={SCENE_COLORS.wrap} />
        <path d="M80 148 L120 148 L118 156 L82 156 Z" fill={SCENE_COLORS.wrapTrim} />
      </svg>
    </div>
  );
}
