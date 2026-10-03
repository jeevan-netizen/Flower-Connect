import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { BLOOM_COLORS, SCENE_COLORS, TOKENS } from "./bouquet-palette";

/**
 * The 3D scene and the static fallback take literal colour strings, so the
 * landing tokens have to be mirrored in TypeScript. That mirror is only safe if
 * something checks it: this test parses `:root` out of the stylesheet and fails
 * when a token and its mirror drift apart, which is the failure mode a comment
 * cannot prevent.
 *
 * Read from disk rather than through a `?raw` import: Vitest replaces CSS module
 * imports with empty strings unless CSS processing is switched on for the whole
 * suite, which would slow every test down to protect one assertion.
 */
const STYLESHEET = readFileSync(resolve(process.cwd(), "src/app/styles/index.css"), "utf8");

function rootTokens(): Map<string, string> {
  const block = /:root\s*\{([\s\S]*?)\n\s*\}/.exec(STYLESHEET);
  expect(block, "the stylesheet must declare a :root block").not.toBeNull();

  const tokens = new Map<string, string>();
  for (const declaration of (block?.[1] ?? "").split(";")) {
    const match = /(--[a-z0-9-]+):\s*([^;]+)/i.exec(declaration);
    if (match) {
      tokens.set(match[1], match[2].trim().toLowerCase());
    }
  }
  return tokens;
}

describe("bouquet palette", () => {
  it("mirrors the landing design tokens declared in the stylesheet", () => {
    const tokens = rootTokens();

    for (const [token, mirrored] of Object.entries(TOKENS)) {
      expect(tokens.get(token), `${token} must be declared in :root`).toBeDefined();
      expect(mirrored, `${token} mirror`).toBe(tokens.get(token));
    }
  });

  it("exposes the scene and bloom colours as CSS hex, from that single mirror", () => {
    for (const value of [...Object.values(BLOOM_COLORS), ...Object.values(SCENE_COLORS)]) {
      expect(value).toMatch(/^#[0-9a-f]{6}$/);
    }
    // One value per token: the structural and bloom views cannot disagree.
    expect(SCENE_COLORS.stem).toBe(BLOOM_COLORS.leafDark);
    expect(SCENE_COLORS.leaf).toBe(BLOOM_COLORS.leaf);
    expect(SCENE_COLORS.wrap).toBe(BLOOM_COLORS.plum);
    expect(SCENE_COLORS.wrapTrim).toBe(BLOOM_COLORS.roseDeep);
  });
});
