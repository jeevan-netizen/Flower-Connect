export default {
  root: true,
  extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended", "prettier"],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: 2020,
    sourceType: "module",
    project: ["./tsconfig.json"],
  },
  plugins: ["@typescript-eslint", "react"],
  settings: {
    react: { version: "detect" },
  },
  rules: {
    "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
    "react/react-in-jsx-scope": "off",
    "react/prop-types": "off",
  },
  ignorePatterns: ["dist", "node", "vite.config.ts"],
};