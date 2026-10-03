import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FlowerSuccess } from "@/motion/FlowerSuccess";
import { FlowerLoader } from "@/motion/FlowerLoader";

describe("FlowerSuccess", () => {
  it("announces the confirmation as status text", () => {
    render(<FlowerSuccess message="Profile saved" />);

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Profile saved");
  });

  it("hides the flower and checkmark from assistive technology", () => {
    const { container } = render(<FlowerSuccess message="Profile saved" />);

    // The animation is decoration. A screen reader should hear the sentence and
    // nothing else, so the graphic carries no accessible name.
    const graphic = container.querySelector("svg");
    expect(graphic).not.toBeNull();
    expect(graphic?.closest("[aria-hidden='true']")).not.toBeNull();
    expect(screen.getByRole("status")).toHaveAccessibleName("");
  });

  it("uses the CSS enter animation, so reduced motion disables it without hiding the message", () => {
    const { container } = render(<FlowerSuccess message="Profile saved" />);

    expect(container.querySelector(".fc-bloom-in")).not.toBeNull();
    expect(screen.getByText("Profile saved")).toBeVisible();
  });
});

describe("FlowerLoader", () => {
  it("exposes a status role carrying the label", () => {
    render(<FlowerLoader label="Checking your session" />);

    expect(screen.getByRole("status")).toHaveTextContent("Checking your session");
  });

  it("defaults to a generic label", () => {
    render(<FlowerLoader />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading");
  });

  it("hides the petals from assistive technology", () => {
    const { container } = render(<FlowerLoader />);

    const petals = container.querySelector(".fc-flower-spinner");
    expect(petals).not.toBeNull();
    expect(petals?.getAttribute("aria-hidden")).toBe("true");
  });

  it("shows the label visibly when asked", () => {
    render(<FlowerLoader label="Loading users..." showLabel />);

    const label = screen.getByText("Loading users...");
    expect(label).toBeVisible();
  });
});
