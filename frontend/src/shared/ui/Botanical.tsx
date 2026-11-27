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

/** A layered flower head, used for the landing bouquet and small sprigs. */
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
  const petals = [0, 45, 90, 135, 180, 225, 270, 315];
  return (
    <g transform={`translate(${cx} ${cy}) scale(${scale})`}>
      {petals.map((angle) => (
        <ellipse
          key={angle}
          cx="0"
          cy="-11"
          rx="9"
          ry="15"
          className={petal}
          transform={`rotate(${angle})`}
          opacity="0.82"
        />
      ))}
      {petals.filter((_, index) => index % 2 === 0).map((angle) => (
        <ellipse
          key={`inner-${angle}`}
          cx="0"
          cy="-7"
          rx="6"
          ry="10"
          className={petal}
          transform={`rotate(${angle + 22})`}
          opacity="0.98"
        />
      ))}
      <circle r="5" className={centre} />
      <circle cx="-1.5" cy="-1.5" r="1.5" className={petal} opacity="0.8" />
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
 * The landing hero still life: a soft, sunlit bouquet of rose and blush blooms
 * arranged in a fluted cream ceramic vase.
 */
export function BotanicalHero({ className = "" }: IllustrationProps) {
  return (
    <div
      className={`relative mx-auto w-full max-w-[500px] overflow-visible bg-[radial-gradient(ellipse_at_54%_46%,rgba(239,226,206,0.72)_0%,rgba(248,245,237,0.42)_43%,rgba(248,245,237,0)_74%)] ${className}`}
      data-testid="botanical-hero"
    >
      <svg
        viewBox="0 0 420 520"
        role="presentation"
        aria-hidden="true"
        focusable="false"
        className="h-full w-full"
      >
        <defs>
          <linearGradient id="fc-ceramic-glaze" x1="0" x2="1" y1="0" y2="0">
            <stop offset="0" stopColor="#d5c9b6" />
            <stop offset="0.2" stopColor="#f5efe4" />
            <stop offset="0.5" stopColor="#fffaf0" />
            <stop offset="0.8" stopColor="#e7dccb" />
            <stop offset="1" stopColor="#cfc2af" />
          </linearGradient>
          <radialGradient id="fc-floor-shadow">
            <stop offset="0" stopColor="#82745e" stopOpacity="0.24" />
            <stop offset="1" stopColor="#82745e" stopOpacity="0" />
          </radialGradient>
        </defs>

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

        {/* Soft tabletop shadow anchors the vase in the scene. */}
        <ellipse cx="210" cy="458" rx="128" ry="18" fill="url(#fc-floor-shadow)" />

        {/* Fluted cream ceramic vase, with a visible opening and warm glaze. */}
        <path
          d="M159 327C167 348 171 375 168 403C165 428 168 444 180 452C194 461 226 461 240 452C252 444 255 428 252 403C249 375 253 348 261 327Z"
          fill="url(#fc-ceramic-glaze)"
          stroke="#c9bcaa"
          strokeWidth="1.4"
        />
        <path
          d="M159 327C180 338 240 338 261 327L256 342C236 350 184 350 164 342Z"
          fill="#e8ddce"
          stroke="#c9bcaa"
          strokeWidth="1.1"
        />
        <ellipse cx="210" cy="328" rx="51" ry="9" fill="#cbbdaa" />
        <ellipse cx="210" cy="326" rx="44" ry="5.5" fill="#777061" opacity="0.34" />
        <g fill="none" stroke="#c9bda9" strokeLinecap="round" opacity="0.58">
          <path d="M176 353C181 377 177 420 182 446" />
          <path d="M188 350C192 380 189 431 194 455" />
          <path d="M201 350C203 384 202 435 203 459" />
          <path d="M216 350C215 384 218 435 217 459" />
          <path d="M229 350C225 380 229 431 224 455" />
          <path d="M242 353C237 377 241 420 236 446" />
        </g>
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
