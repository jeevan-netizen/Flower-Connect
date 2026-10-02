/**
 * Static, dependency-free bouquet.
 *
 * Used in three situations, all of which must render something sensible rather
 * than nothing: while the lazy 3D chunk is still downloading (Suspense
 * fallback), when WebGL is unavailable, and when the scene throws or loses its
 * context. It is deliberately plain SVG — no animation, no gradients over
 * animated stops, no JS beyond a static map — so it can be imported eagerly by
 * `HomePage` without pulling Three.js into the main chunk.
 *
 * `HERO_FRAME_CLASS` fixes the frame height at every breakpoint and is shared by
 * the fallback and the canvas, so swapping one for the other cannot shift
 * layout.
 */
export const HERO_FRAME_CLASS =
  "relative h-[240px] w-full overflow-hidden rounded-2xl border border-brand-100 bg-brand-50 sm:h-[320px] lg:h-[400px]";

interface Bloom {
  readonly cx: number;
  readonly cy: number;
  readonly radius: number;
  readonly petals: number;
  readonly petalColor: string;
  readonly coreColor: string;
}

const BLOOMS: readonly Bloom[] = [
  { cx: 66, cy: 74, radius: 15, petals: 5, petalColor: "#fbcfe8", coreColor: "#f472b6" },
  { cx: 100, cy: 52, radius: 18, petals: 6, petalColor: "#fde68a", coreColor: "#fbbf24" },
  { cx: 134, cy: 76, radius: 14, petals: 5, petalColor: "#ede9fe", coreColor: "#c4b5fd" },
  { cx: 84, cy: 106, radius: 11, petals: 5, petalColor: "#fecdd3", coreColor: "#fb7185" },
  { cx: 118, cy: 112, radius: 10, petals: 5, petalColor: "#fce7f3", coreColor: "#f9a8d4" },
];

const STEMS: readonly string[] = [
  "M100 150 L66 74",
  "M100 150 L100 52",
  "M100 150 L134 76",
  "M100 150 L84 106",
  "M100 150 L118 112",
  "M100 150 C 88 118 74 104 60 96",
  "M100 150 C 114 120 132 108 142 100",
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
 * action are the accessible content.
 */
export function HeroFallback({ className }: HeroFallbackProps) {
  return (
    <div
      data-testid="hero-fallback"
      aria-hidden="true"
      className={className ?? HERO_FRAME_CLASS}
    >
      <svg viewBox="0 0 200 200" className="h-full w-full" focusable="false">
        <g stroke="#15803d" strokeWidth="3" strokeLinecap="round" fill="none">
          {STEMS.map((points) => (
            <path key={points} d={points} />
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
        <path d="M84 148 L116 148 L108 186 L92 186 Z" fill="#dcfce7" />
        <path d="M100 148 L100 186" stroke="#22c55e" strokeWidth="2" />
      </svg>
    </div>
  );
}