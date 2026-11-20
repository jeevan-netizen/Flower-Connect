import { describe, it, expect, vi, afterEach } from "vitest";
import { render, fireEvent, cleanup } from "@testing-library/react";
import { PasswordField } from "@/features/auth/components/PasswordField";

const mockRegistration = vi.fn<[string], unknown>((name) => ({
  name,
  onChange: vi.fn(),
  onBlur: vi.fn(),
  value: "",
  disabled: false,
}));

function renderField(props: Partial<Parameters<typeof PasswordField>[0]> = {}) {
  const registration = mockRegistration("password") as unknown as Parameters<
    typeof PasswordField
  >[0]["registration"];
  return render(<PasswordField label="Password" registration={registration} {...props} />);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockRegistration.mockClear();
});

describe("PasswordField", () => {
  it("is masked by default", () => {
    const { container } = renderField();

    const input = container.querySelector("input")!;
    expect(input).toHaveAttribute("type", "password");
  });

  it("reveals the password when toggled to show", () => {
    const { container, getByRole } = renderField();

    const input = container.querySelector("input")!;
    const toggle = getByRole("button", { name: /show password/i });

    fireEvent.click(toggle);

    expect(input).toHaveAttribute("type", "text");
    expect(toggle).toHaveAttribute("aria-pressed", "true");
  });

  it("hides the password again when toggled back", () => {
    const { container, getByRole } = renderField();

    const input = container.querySelector("input")!;
    const toggle = getByRole("button", { name: /show password/i });

    fireEvent.click(toggle);
    fireEvent.click(toggle);

    expect(input).toHaveAttribute("type", "password");
    expect(toggle).toHaveAttribute("aria-pressed", "false");
  });

  it("has an accessible name and pressed state on the toggle", () => {
    const { getByRole } = renderField();

    const toggle = getByRole("button", { name: /show password/i });
    expect(toggle).toHaveAttribute("aria-label", "Show password");
    expect(toggle).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(toggle);

    expect(getByRole("button", { name: /hide password/i })).toHaveAttribute(
      "aria-label",
      "Hide password",
    );
    expect(toggle).toHaveAttribute("aria-pressed", "true");
  });

  it("toggle button is type=button", () => {
    const { getByRole } = renderField();

    const toggle = getByRole("button", { name: /show password/i });
    expect(toggle).toHaveAttribute("type", "button");
  });

  it("toggling one field does not affect another", () => {
    const first = renderField();
    const second = renderField({ label: "Confirm password" });

    const firstInput = first.container.querySelector("input")!;
    const secondInput = second.container.querySelector("input")!;
    const secondToggle = second.container.querySelector("button")!;

    fireEvent.click(secondToggle);

    expect(firstInput).toHaveAttribute("type", "password");
    expect(secondInput).toHaveAttribute("type", "text");
  });
});
