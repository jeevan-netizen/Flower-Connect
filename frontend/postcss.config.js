export default {
  plugins: {
    // `tailwindcss` must come before `autoprefixer`. Without it the `@tailwind`
    // directives in `src/app/styles/index.css` were shipped to the browser
    // verbatim (the built CSS asset was 57 bytes and the app rendered with no
    // utilities at all).
    tailwindcss: {},
    autoprefixer: {},
  },
};