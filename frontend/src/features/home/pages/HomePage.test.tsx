import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { HomePage } from "./HomePage";

/**
 * `HomePage` lazy-loads the bouquet, so these tests drive the pending state
 * rather than waiting for a chunk: the stub suspends forever on demand and is
 * released for the second case. What matters here is the page's own contract —
 * the heading, copy and calls to action are outside the canvas, in the initial
 * chunk, and usable while the 3D scene is still loading or has failed.
 */
const hero = vi.hoisted(() => ({ pending: true }));

vi.mock("@/features/home/hero/BouquetHero", () => ({
  default: function BouquetHeroStub() {
    if (hero.pending) {
      throw new Promise<void>(() => {});
    }
    return <div data-testid="stub-hero" />;
  },
}));

function renderHome() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/browse" element={<p>Browse destination</p>} />
        <Route path="/register" element={<p>Register destination</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  hero.pending = false;
});

describe("HomePage", () => {
  it("renders the heading, copy and calls to action while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();
    // Let the dynamic import settle; the stub keeps suspending afterwards, so
    // the fallback is still what the visitor sees.
    await act(async () => {});

    expect(screen.getByRole("heading", { name: "FlowerConnect" })).toBeInTheDocument();
    expect(screen.getByText("Hyperlocal flower marketplace")).toBeInTheDocument();
    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
  });

  it("keeps the calls to action clickable while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.click(screen.getByRole("link", { name: /browse flowers/i }));
    expect(await screen.findByText("Browse destination")).toBeInTheDocument();
  });

  it("keeps the registration call to action clickable while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.click(screen.getByRole("link", { name: /create an account/i }));
    expect(await screen.findByText("Register destination")).toBeInTheDocument();
  });

  it("mounts the scene without disturbing the page once it has loaded", async () => {
    hero.pending = false;
    renderHome();

    expect(await screen.findByTestId("stub-hero")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "FlowerConnect" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /browse flowers/i })).toHaveAttribute("href", "/browse");
    expect(screen.getByRole("link", { name: /create an account/i })).toHaveAttribute("href", "/register");
  });

  it("composes the shared press feedback on both calls to action", async () => {
    hero.pending = true;
    renderHome();

    for (const name of [/browse flowers/i, /create an account/i]) {
      const cta = screen.getByRole("link", { name });
      expect(cta.className).toContain("active:scale-press");
      expect(cta.className).toContain("motion-reduce:active:scale-100");
      expect(cta.className).toContain("focus-visible:ring-brand-500");
    }
  });
});