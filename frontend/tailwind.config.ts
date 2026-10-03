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
        // Landing design tokens, mirrored from the `--color-*`, `--glass-*`,
        // `--glow-*` and `--shadow-*` custom properties in
        // `src/app/styles/index.css`. Values resolve at runtime from those
        // properties, so a theme swap is a change to `:root` alone and no
        // component carries a hex value.
        bolder: {
          bg: "var(--color-bg)",
          text: "var(--color-text)",
          muted: "var(--color-text-muted)",
          placeholder: "var(--color-placeholder)",
          rose: "var(--color-rose)",
          "rose-strong": "var(--color-rose-strong)",
          "rose-deep": "var(--color-rose-deep)",
          "rose-action": "var(--color-rose-action)",
          blush: "var(--color-blush)",
          "blush-light": "var(--color-blush-light)",
          leaf: "var(--color-leaf)",
          "leaf-text": "var(--color-leaf-text)",
          "leaf-dark": "var(--color-leaf-dark)",
          gold: "var(--color-gold)",
          plum: "var(--color-plum)",
          "tile-leaf": "var(--tile-leaf)",
          "tile-rose": "var(--tile-rose)",
        },
        glass: {
          DEFAULT: "var(--glass-bg)",
          soft: "var(--glass-bg-soft)",
          border: "var(--glass-border)",
          "border-soft": "var(--glass-border-soft)",
        },
      },
      backgroundImage: {
        "glow-rose": "var(--glow-rose)",
        "glow-leaf": "var(--glow-leaf)",
      },
      boxShadow: {
        glass: "var(--shadow-glass)",
        search: "var(--shadow-search)",
        button: "var(--shadow-button)",
      },
      borderRadius: {
        pill: "var(--radius-pill)",
        card: "var(--radius-card)",
        chip: "var(--radius-chip)",
        "glass-card": "var(--radius-glass-card)",
      },
      maxWidth: {
        landing: "1168px",
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
        display: ["var(--font-display)"],
        body: ["var(--font-body)"],
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