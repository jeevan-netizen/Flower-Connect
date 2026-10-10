import { describe, it, expect, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LocationPicker } from "@/features/location/components/LocationPicker";
import { renderWithProviders } from "@/test/render";
import { makeServiceLocations } from "@/test/factories";

describe("LocationPicker", () => {
  it("offers every seeded area grouped by city, with the id as the value", () => {
    renderWithProviders(
      <LocationPicker cities={makeServiceLocations()} value="" onChange={vi.fn()} />,
    );

    const picker = screen.getByLabelText(/delivery location/i);
    expect(
      within(picker).getByRole("option", { name: "Indiranagar (560038)" }),
    ).toHaveValue("3");
    expect(within(picker).getByRole("option", { name: "Koramangala (560034)" })).toHaveValue("4");
    // The city grouping is the plan's "city / area / pincode selector".
    expect(within(picker).getByRole("group", { name: "Bengaluru" })).toBeInTheDocument();
    // Nothing is preselected: task 4.2 defines no default location.
    expect(picker).toHaveValue("");
    expect(within(picker).getByRole("option", { name: /choose your delivery area/i })).toHaveValue("");
  });

  it("shows the chosen value", () => {
    renderWithProviders(
      <LocationPicker cities={makeServiceLocations()} value="4" onChange={vi.fn()} />,
    );

    expect(screen.getByLabelText(/delivery location/i)).toHaveValue("4");
  });

  it("reports the picked id through onChange", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(
      <LocationPicker cities={makeServiceLocations()} value="" onChange={onChange} />,
    );

    await user.selectOptions(screen.getByLabelText(/delivery location/i), "4");

    expect(onChange).toHaveBeenCalledWith("4");
  });

  it("stays disabled while the areas are unavailable, and says why", () => {
    renderWithProviders(
      <LocationPicker
        cities={undefined}
        value=""
        disabled
        hint="Loading delivery areas..."
        onChange={vi.fn()}
      />,
    );

    const picker = screen.getByLabelText(/delivery location/i);
    expect(picker).toBeDisabled();
    expect(screen.getByText("Loading delivery areas...")).toBeInTheDocument();
    // Only the placeholder is offered: a picker full of options it cannot
    // validate is worse than an empty one.
    expect(within(picker).getAllByRole("option")).toHaveLength(1);
  });

  it("replaces the hint with the error message in the described-by element", () => {
    renderWithProviders(
      <LocationPicker
        cities={makeServiceLocations()}
        value=""
        error="Choose an area to continue"
        onChange={vi.fn()}
      />,
    );

    const picker = screen.getByLabelText(/delivery location/i);
    expect(picker).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Choose an area to continue")).toBeInTheDocument();
    expect(screen.queryByText(/we use this area/i)).not.toBeInTheDocument();
  });

  it("is keyboard operable: the select is a native control with a real label", () => {
    renderWithProviders(
      <LocationPicker cities={makeServiceLocations()} value="" onChange={vi.fn()} />,
    );

    // A native <select> is arrow-key operable by default; the label is what
    // makes it announceable, and `aria-describedby` ties the hint to it.
    const picker = screen.getByLabelText(/delivery location/i);
    expect(picker.tagName).toBe("SELECT");
    expect(picker).toHaveAccessibleDescription(/we use this area/i);
  });
});
