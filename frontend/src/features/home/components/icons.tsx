import type { SVGProps } from "react";

/**
 * The landing page's line icons.
 *
 * One stroke-based set, drawn on a 24×24 grid with a consistent 1.6 stroke, so
 * the badge, the two floating cards and the feature row share one icon voice.
 * Colour comes from the surrounding text (`currentColor`), which keeps every use
 * on the token palette without an icon knowing which token it is.
 *
 * All of them are decorative: each icon sits beside real text, so every icon is
 * `aria-hidden` and `focusable="false"` rather than being given a label of its
 * own.
 */
type IconProps = SVGProps<SVGSVGElement>;

function Icon({ children, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

export function MapPinIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 21s-7-5.3-7-10a7 7 0 1 1 14 0c0 4.7-7 10-7 10Z" />
      <circle cx="12" cy="11" r="2.5" />
    </Icon>
  );
}

export function ClockIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5.2l3.4 2" />
    </Icon>
  );
}

export function ShopIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 10h16v10H4z" />
      <path d="M3 6.5 4.6 10h14.8L21 6.5z" />
      <path d="M9.5 20v-5.5h5V20" />
    </Icon>
  );
}

export function CalendarIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="3.5" y="5.5" width="17" height="15" rx="2.5" />
      <path d="M3.5 10h17M8 3.5v4M16 3.5v4" />
      <path d="m9 15.5 2 2 4-4" />
    </Icon>
  );
}

export function CheckCircleIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12.2 2.6 2.6L16 9.4" />
    </Icon>
  );
}
