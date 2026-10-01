import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorHoursPage } from "@/features/vendor/pages/VendorHoursPage";
import { renderWithProviders } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import { businessError, vendorNotApprovedError } from "@/test/api-errors";
import type { VendorHours, VendorProfile, VendorProfileUpdateRequest } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

function setup(profile: VendorProfile = makeVendorProfile()) {
  mockFetch.mockResolvedValue(profile);
  return renderWithProviders(<VendorHoursPage />);
}

function rowFor(day: string): HTMLElement {
  const listItem = screen.getByText(day).closest("li");
  if (!listItem) {
    throw new Error(`No row rendered for ${day}`);
  }
  return listItem;
}

async function waitForForm() {
  // The rows render immediately with empty defaults; the stored week only lands
  // once the profile query resolves and re-seeds the form.
  await waitFor(() => {
    expect(within(rowFor("Monday")).getByLabelText(/opens/i)).toHaveValue("09:00");
  });
}

describe("VendorHoursPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdate.mockResolvedValue(makeVendorProfile());
  });

  it("renders all seven weekdays, not only the stored ones", async () => {
    setup();
    await waitForForm();

    ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"].forEach((day) => {
      expect(screen.getByText(day)).toBeInTheDocument();
    });
  });

  it("renders the stored hours for an open day", async () => {
    setup();
    await waitForForm();

    const monday = rowFor("Monday");
    expect(within(monday).getByLabelText("Monday")).toBeChecked();
    expect(within(monday).getByLabelText(/opens/i)).toHaveValue("09:00");
    expect(within(monday).getByLabelText(/closes/i)).toHaveValue("18:00");
  });

  it("renders a day with no stored row as closed with its times disabled", async () => {
    setup();
    await waitForForm();

    const tuesday = rowFor("Tuesday");
    expect(within(tuesday).getByLabelText("Tuesday")).not.toBeChecked();
    expect(within(tuesday).getByLabelText(/opens/i)).toBeDisabled();
    expect(within(tuesday).getByLabelText(/closes/i)).toHaveValue("");
  });

  it("submits the full week in the backend LocalTime representation", async () => {
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });

    const payload = mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest;
    const hours = payload.hours as VendorHours[];

    expect(hours).toHaveLength(7);
    expect(hours[0]).toEqual({
      weekday: "MONDAY",
      closed: false,
      openTime: "09:00:00",
      closeTime: "18:00:00",
    });
    expect(hours[1]).toEqual({ weekday: "TUESDAY", closed: true, openTime: null, closeTime: null });
  });

  it("opens a previously closed day and sends its times", async () => {
    setup();
    await waitForForm();

    const tuesday = rowFor("Tuesday");
    await userEvent.click(within(tuesday).getByLabelText("Tuesday"));
    await userEvent.type(within(tuesday).getByLabelText(/opens/i), "10:00");
    await userEvent.type(within(tuesday).getByLabelText(/closes/i), "16:00");

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });

    const hours = (mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest).hours as VendorHours[];
    expect(hours[1]).toEqual({ weekday: "TUESDAY", closed: false, openTime: "10:00:00", closeTime: "16:00:00" });
  });

  it("clears the times of a day that is switched back to closed", async () => {
    setup();
    await waitForForm();

    const monday = rowFor("Monday");
    await userEvent.click(within(monday).getByLabelText("Monday"));

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });

    const hours = (mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest).hours as VendorHours[];
    expect(hours[0]).toEqual({ weekday: "MONDAY", closed: true, openTime: null, closeTime: null });
  });

  it("rejects an open day with no opening time", async () => {
    setup();
    await waitForForm();

    const tuesday = rowFor("Tuesday");
    await userEvent.click(within(tuesday).getByLabelText("Tuesday"));
    await userEvent.type(within(tuesday).getByLabelText(/closes/i), "16:00");

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    expect(
      await within(tuesday).findByText(/opening time is required when the day is open/i),
    ).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("rejects a day that closes before it opens", async () => {
    setup();
    await waitForForm();

    const monday = rowFor("Monday");
    await userEvent.clear(within(monday).getByLabelText(/closes/i));
    await userEvent.type(within(monday).getByLabelText(/closes/i), "08:00");

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    expect(
      await within(monday).findByText(/closing time must be after opening time/i),
    ).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("rejects a day that closes exactly when it opens", async () => {
    setup();
    await waitForForm();

    const monday = rowFor("Monday");
    await userEvent.clear(within(monday).getByLabelText(/closes/i));
    await userEvent.type(within(monday).getByLabelText(/closes/i), "09:00");

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    expect(
      await within(monday).findByText(/closing time must be after opening time/i),
    ).toBeInTheDocument();
  });

  it("surfaces a backend cross-field hours message", async () => {
    mockUpdate.mockRejectedValue(
      businessError(400, "VALIDATION_FAILED", "Opening hours for MONDAY must close after they open"),
    );
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    expect(await screen.findByText(/must close after they open/i)).toBeInTheDocument();
  });

  it("shows approval guidance instead of a login redirect when the backend refuses", async () => {
    mockFetch.mockRejectedValue(vendorNotApprovedError());

    renderWithProviders(<VendorHoursPage />);

    expect(await screen.findByText(/approval required/i)).toBeInTheDocument();
    expect(
      screen.getByText(/available to approved vendors only/i),
    ).toBeInTheDocument();
  });

  it("confirms a successful save", async () => {
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save operating hours/i }));

    expect(await screen.findByText(/operating hours saved/i)).toBeInTheDocument();
  });

  it("shows a loading state before the profile arrives", async () => {
    let resolveProfile: (value: VendorProfile) => void = () => {};
    mockFetch.mockImplementation(
      () => new Promise<VendorProfile>((resolve) => {
        resolveProfile = resolve;
      }),
    );

    renderWithProviders(<VendorHoursPage />);

    expect(await screen.findByText(/loading your operating hours/i)).toBeInTheDocument();

    resolveProfile(makeVendorProfile());

    await waitForForm();
  });
});
