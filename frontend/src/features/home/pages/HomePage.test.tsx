import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { HomePage } from "./HomePage";

/**
 * `HomePage` lazy-loads the bouquet, so these tests drive the pending state
 * rather than waiting for a chunk: the stub suspends forever on demand and is
 * released for the cases that need the scene mounted. What matters here is the
 * page's own contract — the heading, copy, search, calls to action, feature row
 * and decoration are outside the canvas, in the initial chunk, and usable while
 * the 3D scene is still loading or has failed.
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

/** Reports where the hero's search sent the visitor, query string included. */
function BrowseDestination() {
  const location = useLocation();
  return <p>Browse destination {location.search}</p>;
}

function renderHome() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/browse" element={<BrowseDestination />} />
        <Route path="/vendor/register" element={<p>Florist registration destination</p>} />
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

    expect(
      screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Local florists near you")).toBeInTheDocument();
    expect(screen.getByText(/discover independent florists/i)).toBeInTheDocument();
    expect(screen.getByTestId("hero-fallback")).toBeInTheDocument();
  });

  it("gives the search field a real label rather than only a placeholder", () => {
    hero.pending = true;
    renderHome();

    const input = screen.getByRole("searchbox", { name: "Your area or pincode" });
    expect(input).toHaveAttribute("placeholder", "Enter your area or pincode");
  });

  it("carries the typed area to the browse route as a query parameter", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.type(screen.getByRole("searchbox", { name: /area or pincode/i }), "Indiranagar");
    await userEvent.click(screen.getByRole("button", { name: "Explore Flowers" }));

    expect(await screen.findByText("Browse destination ?area=Indiranagar")).toBeInTheDocument();
  });

  it("sends an empty search to the browse route without a query string", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.click(screen.getByRole("button", { name: "Explore Flowers" }));

    expect(await screen.findByText("Browse destination")).toBeInTheDocument();
  });

  it("points the florist call to action at the existing vendor registration route", () => {
    hero.pending = true;
    renderHome();

    expect(screen.getByRole("link", { name: /become a florist/i })).toHaveAttribute(
      "href",
      "/vendor/register",
    );
    expect(screen.getByText(/open your shop in minutes/i)).toBeInTheDocument();
  });

  it("keeps the florist call to action usable while the scene is still loading", async () => {
    hero.pending = true;
    renderHome();

    await userEvent.click(screen.getByRole("link", { name: /become a florist/i }));

    expect(await screen.findByText("Florist registration destination")).toBeInTheDocument();
  });

  it("describes the bouquet in text for anyone who never sees the canvas", () => {
    hero.pending = true;
    renderHome();

    expect(screen.getByText(/hand-tied bouquet of rose, blush and gold/i)).toBeInTheDocument();
  });

  it("hides the decorative background layer from assistive technology and the pointer", () => {
    hero.pending = true;
    const { container } = renderHome();

    const backdrop = container.querySelector('[aria-hidden="true"].pointer-events-none');
    expect(backdrop).not.toBeNull();
    expect(backdrop?.className).toContain("absolute");
  });

  it("lists the four capabilities with no invented numbers or social proof", () => {
    hero.pending = true;
    renderHome();

    expect(
      screen.getByRole("heading", { name: /what flowerconnect does/i }),
    ).toBeInTheDocument();
    for (const title of [
      "Local florists",
      "Same-day delivery",
      "Scheduled delivery",
      "Simple ordering",
    ]) {
      expect(screen.getByText(title)).toBeInTheDocument();
    }
    expect(screen.queryByText(/\d+\+?\s*(rating|review|customer|florist)/i)).not.toBeInTheDocument();
  });

  it("mounts the scene without disturbing the page once it has loaded", async () => {
    hero.pending = false;
    renderHome();

    expect(await screen.findByTestId("stub-hero")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /become a florist/i })).toHaveAttribute(
      "href",
      "/vendor/register",
    );
  });

  it("composes the shared press feedback and focus ring on the search button", () => {
    hero.pending = true;
    renderHome();

    const cta = screen.getByRole("button", { name: "Explore Flowers" });
    expect(cta.className).toContain("active:scale-press");
    expect(cta.className).toContain("motion-reduce:active:scale-100");
    expect(cta.className).toContain("focus-visible:ring-bolder-rose");
  });

  it("gives every hero control a visible focus indicator", () => {
    hero.pending = true;
    renderHome();

    // The search field's own outline is suppressed because the pill around it
    // lights up instead — so the ring has to be asserted on the form, not on the
    // input, or the suppression would be an invisible focus state.
    const form = screen.getByRole("search");
    expect(form.className).toContain("focus-within:ring-bolder-rose");
    expect(
      screen.getByRole("searchbox", { name: /area or pincode/i }).className,
    ).toContain("outline-none");

    for (const control of [
      screen.getByRole("button", { name: "Explore Flowers" }),
      screen.getByRole("link", { name: /become a florist/i }),
    ]) {
      expect(control.className).toContain("focus-visible:ring-bolder-rose");
    }
  });
});
