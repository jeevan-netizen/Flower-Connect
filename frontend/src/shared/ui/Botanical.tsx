/**
 * Original inline botanicals for the editorial surface.
 *
 * These are drawn here rather than shipped as photographs: no image asset, no
 * stock licence, no network request and no fake product. Every fill and stroke
 * is a design token, so the illustration re-tints with the rest of the system.
 *
 * Both components are decorative. The page that uses them supplies the text a
 * screen reader hears, so the SVGs are hidden from assistive technology and
 * cannot take focus.
 */

interface IllustrationProps {
  className?: string;
}

/** A rose head made of six overlapping petals and a centre. */
function Rose({
  cx,
  cy,
  scale = 1,
  petal,
  centre,
}: {
  cx: number;
  cy: number;
  scale?: number;
  petal: string;
  centre: string;
}) {
  const petals = [0, 60, 120, 180, 240, 300];
  return (
    <g transform={`translate(${cx} ${cy}) scale(${scale})`}>
      {petals.map((angle) => (
        <ellipse
          key={angle}
          cx="0"
          cy="-12"
          rx="10"
          ry="14"
          className={petal}
          transform={`rotate(${angle})`}
          opacity="0.92"
        />
      ))}
      <circle r="5" className={centre} />
    </g>
  );
}

/** A leaf pair on a stem. */
function Leaf({
  x,
  y,
  rotate,
  flip = false,
  className = "fill-bolder-leaf",
}: {
  x: number;
  y: number;
  rotate: number;
  flip?: boolean;
  className?: string;
}) {
  return (
    <path
      d="M0 0C14 -8 32 -4 38 10C22 16 6 12 0 0Z"
      transform={`translate(${x} ${y}) rotate(${rotate}) scale(${flip ? -1 : 1} 1)`}
      className={className}
      opacity="0.9"
    />
  );
}

/**
 * The landing hero still life: an arched paper panel holding a hand-tied
 * bouquet of rose, blush and gold blooms with botanical foliage.
 */
export function BotanicalHero({ className = "" }: IllustrationProps) {
  return (
    <div
      className={`relative mx-auto w-full max-w-[460px] overflow-hidden rounded-t-[230px] rounded-b-card border border-bolder-line bg-[radial-gradient(circle_at_50%_18%,var(--color-blush-light)_0%,var(--color-surface)_62%,var(--color-surface-muted)_100%)] shadow-card ${className}`}
      data-testid="botanical-hero"
    >
      <svg
        viewBox="0 0 420 520"
        role="presentation"
        aria-hidden="true"
        focusable="false"
        className="h-full w-full"
      >
        {/* A soft ring behind the arrangement, echoing a florist's paper cone. */}
        <circle
          cx="210"
          cy="205"
          r="150"
          fill="none"
          className="stroke-bolder-blush"
          strokeWidth="1"
          strokeDasharray="3 9"
          opacity="0.9"
        />

        {/* Stems. */}
        <g fill="none" className="stroke-bolder-leaf" strokeLinecap="round">
          <path d="M210 330C204 280 196 240 186 206" strokeWidth="3" />
          <path d="M214 330C222 278 240 238 262 210" strokeWidth="3" />
          <path d="M208 330C198 290 176 266 152 250" strokeWidth="2.5" />
          <path d="M216 330C230 294 252 274 278 262" strokeWidth="2.5" />
          <path d="M210 330C210 290 208 262 206 232" strokeWidth="2.5" />
          <path d="M212 330C218 300 236 288 252 282" strokeWidth="2" />
        </g>

        {/* Foliage. */}
        <Leaf x={178} y={268} rotate={-38} />
        <Leaf x={244} y={280} rotate={32} flip />
        <Leaf x={196} y={236} rotate={-62} />
        <Leaf x={232} y={238} rotate={58} flip />
        <Leaf x={156} y={286} rotate={-18} />
        <Leaf x={272} y={296} rotate={22} flip />
        <Leaf x={202} y={300} rotate={-94} className="fill-bolder-leaf-dark" />
        <Leaf x={226} y={308} rotate={96} flip className="fill-bolder-leaf-dark" />

        {/* Blooms. */}
        <Rose cx={186} cy={196} petal="fill-bolder-rose" centre="fill-bolder-rose-deep" />
        <Rose cx={266} cy={202} scale={0.92} petal="fill-bolder-blush" centre="fill-bolder-gold" />
        <Rose cx={150} cy={246} scale={0.8} petal="fill-bolder-rose-strong" centre="fill-bolder-blush" />
        <Rose cx={282} cy={256} scale={0.74} petal="fill-bolder-gold" centre="fill-bolder-rose-deep" />
        <Rose cx={208} cy={166} scale={0.66} petal="fill-bolder-rose" centre="fill-bolder-gold" />
        <Rose cx={236} cy={240} scale={0.58} petal="fill-bolder-blush-light" centre="fill-bolder-rose-strong" />

        {/* A few loose buds. */}
        <circle cx="138" cy="214" r="5" className="fill-bolder-rose" opacity="0.75" />
        <circle cx="298" cy="226" r="4" className="fill-bolder-blush" opacity="0.9" />
        <circle cx="252" cy={170} r="3.5" className="fill-bolder-gold" opacity="0.9" />

        {/* Wrapped paper. */}
        <path
          d="M154 326H266L246 430C240 458 180 458 174 430L154 326Z"
          className="fill-bolder-surface-muted stroke-bolder-line-strong"
          strokeWidth="1.2"
        />
        <path
          d="M154 326C176 340 244 340 266 326"
          fill="none"
          className="stroke-bolder-line-strong"
          strokeWidth="1.2"
        />
        <path
          d="M175 344C190 352 230 352 245 344"
          fill="none"
          className="stroke-bolder-line"
          strokeWidth="1"
        />
        {/* A paper band. */}
        <path
          d="M170 374C194 382 226 382 250 374L247 392C222 400 198 400 173 392Z"
          className="fill-bolder-blush"
          opacity="0.85"
        />
      </svg>
    </div>
  );
}

/** A small sprig for section rules and empty states. */
export function BotanicalSprig({ className = "" }: IllustrationProps) {
  return (
    <svg
      viewBox="0 0 120 32"
      role="presentation"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path d="M8 16H112" className="stroke-bolder-line" strokeWidth="1" fill="none" />
      <g transform="translate(60 16)">
        <path d="M0 12V-8" className="stroke-bolder-leaf" strokeWidth="1.5" fill="none" />
        <Leaf x={-2} y={-2} rotate={150} />
        <Leaf x={2} y={2} rotate={30} flip />
        <Rose cx={0} cy={-10} scale={0.42} petal="fill-bolder-rose" centre="fill-bolder-gold" />
      </g>
    </svg>
  );
}
