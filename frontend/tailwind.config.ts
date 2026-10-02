import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f0fdf4",
          100: "#dcfce7",
          500: "#22c55e",
          600: "#16a34a",
          700: "#15803d",
          900: "#14532d",
        },
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      // Motion tokens, mirroring `src/motion/tokens.ts` through the
      // `--fc-motion-*` custom properties declared in
      // `src/app/styles/index.css`. `duration-ui ease-standard` and
      // `{ duration: durations.ui, ease: easings.standard }` are the same
      // motion; components must not write a raw millisecond value.
      transitionDuration: {
        micro: "var(--fc-motion-duration-micro)",
        ui: "var(--fc-motion-duration-ui)",
        page: "var(--fc-motion-duration-page)",
        ambient: "var(--fc-motion-duration-ambient)",
      },
      transitionTimingFunction: {
        standard: "var(--fc-motion-ease-standard)",
        enter: "var(--fc-motion-ease-enter)",
        exit: "var(--fc-motion-ease-exit)",
      },
      transitionDelay: {
        stagger: "var(--fc-motion-stagger)",
      },
      // Press feedback for buttons. Mirrors `distances.press` in
      // `src/motion/tokens.ts`; `active:scale-press` is the whole effect.
      scale: {
        press: "0.98",
      },
    },
  },
  plugins: [],
} satisfies Config;