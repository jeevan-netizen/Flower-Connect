/**
 * Decorative botanicals for the editorial surface.
 *
 * The landing hero uses the flower artwork cropped from the user's supplied
 * design reference. Small botanical sprigs remain native SVG.
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
 * The landing hero still life, using the supplied photo reference artwork.
 */
export function BotanicalHero({ className = "" }: IllustrationProps) {
  return (
    <div
      className={`relative mx-auto aspect-[308/311] w-full max-w-[360px] overflow-hidden ${className}`}
      data-testid="botanical-hero"
    >
      <img
        src="/images/landing-bouquet.webp?ref=landing-hero-20261128"
        alt=""
        aria-hidden="true"
        className="absolute inset-0 h-full w-full object-contain"
      />
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
