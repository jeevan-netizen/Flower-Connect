import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        /*
         * `brand` is the primary action ramp. It is rose, not green: the
         * editorial system uses rose for buttons, links and focus rings, while
         * botanical green lives on `leaf` for accents. Existing screens that
         * already name `brand-*` therefore become rose without a component
         * change, and the two roles cannot drift apart.
         */
        brand: {
          50: "#f9e9ec",
          100: "#f4dce1",
          200: "#ebc4cb",
          300: "#e0a8b3",
          400: "#ce8291",
          500: "#b96b7b",
          600: "#9c4c5f",
          700: "#7d3547",
          800: "#652a39",
          900: "#4e1f2c",
          950: "#33131c",
        },
        /*
         * Botanical green. Used for accents, success and the hero illustration;
         * never for the primary button.
         */
        leaf: {
          50: "#eef3ec",
          100: "#dfe9de",
          200: "#c4d5c4",
          300: "#a3bda5",
          400: "#7e9c82",
          500: "#55705c",
          600: "#465e4c",
          700: "#3e5545",
          800: "#334538",
          900: "#2b3a2f",
        },
        /*
         * Warm stone replaces Tailwind's cool slate so every existing
         * `bg-slate-*` / `text-slate-*` / `border-slate-*` resolves to the
         * editorial palette. `slate-300` is the accessible control border
         * (3.75:1 against the ivory canvas); `slate-200` stays light for
         * dividers.
         */
        slate: {
          50: "#faf6ef",
          100: "#f4ede4",
          200: "#e3d7cb",
          300: "#8f7b6c",
          400: "#75665d",
          500: "#6b5e57",
          600: "#5b4f48",
          700: "#4a403a",
          800: "#3d3531",
          900: "#241f1c",
          950: "#171311",
        },
        // The card surface. Overriding `white` warms every existing `bg-white`
        // card without touching the pages that use it.
        white: "#fffcf7",
        // Landing design tokens, mirrored from the `--color-*`, `--glass-*`,
        // `--glow-*` and `--shadow-*` custom properties in
        // `src/app/styles/index.css`. Values resolve at runtime from those
        // properties, so a theme swap is a change to `:root` alone and no
        // component carries a hex value.
        bolder: {
          bg: "var(--color-bg)",
          surface: "var(--color-surface)",
          "surface-muted": "var(--color-surface-muted)",
          text: "var(--color-text)",
          muted: "var(--color-text-muted)",
          placeholder: "var(--color-placeholder)",
          line: "var(--color-line)",
          "line-strong": "var(--color-line-strong)",
          "line-control": "var(--color-line-control)",
          rose: "var(--color-rose)",
          "rose-strong": "var(--color-rose-strong)",
          "rose-deep": "var(--color-rose-deep)",
          "rose-action": "var(--color-rose-action)",
          blush: "var(--color-blush)",
          "blush-light": "var(--color-blush-light)",
          leaf: "var(--color-leaf)",
          "leaf-text": "var(--color-leaf-text)",
          "leaf-dark": "var(--color-leaf-dark)",
          "leaf-surface": "var(--color-leaf-surface)",
          gold: "var(--color-gold)",
          plum: "var(--color-plum)",
          "tile-leaf": "var(--tile-leaf)",
          "tile-rose": "var(--tile-rose)",
          overlay: "var(--color-overlay)",
        },
        glass: {
          DEFAULT: "var(--glass-bg)",
          soft: "var(--glass-bg-soft)",
          border: "var(--glass-border)",
          "border-soft": "var(--glass-border-soft)",
        },
        // Semantic status pairs. Status meanings are never expressed with
        // `brand` (rose) or `leaf` (green) directly, so a future primary-colour
        // change cannot accidentally restyle an error.
        success: {
          DEFAULT: "var(--color-success)",
          surface: "var(--color-success-surface)",
          border: "var(--color-success-border)",
        },
        warning: {
          DEFAULT: "var(--color-warning)",
          surface: "var(--color-warning-surface)",
          border: "var(--color-warning-border)",
        },
        danger: {
          DEFAULT: "var(--color-danger)",
          surface: "var(--color-danger-surface)",
          border: "var(--color-danger-border)",
        },
        info: {
          DEFAULT: "var(--color-info)",
          surface: "var(--color-info-surface)",
          border: "var(--color-info-border)",
        },
        neutral: {
          DEFAULT: "var(--color-neutral)",
          surface: "var(--color-neutral-surface)",
          border: "var(--color-neutral-border)",
        },
        disabled: {
          DEFAULT: "var(--color-disabled)",
          surface: "var(--color-disabled-surface)",
        },
        focus: {
          ring: "var(--color-focus-ring)",
          offset: "var(--color-focus-ring-offset)",
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
        card: "var(--shadow-card)",
      },
      borderRadius: {
        pill: "var(--radius-pill)",
        card: "var(--radius-card)",
        chip: "var(--radius-chip)",
        control: "var(--radius-control)",
        "glass-card": "var(--radius-glass-card)",
      },
      maxWidth: {
        landing: "1168px",
        content: "1120px",
        app: "1280px",
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