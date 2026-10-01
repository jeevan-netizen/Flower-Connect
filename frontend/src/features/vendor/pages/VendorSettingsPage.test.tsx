import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorSettingsPage } from "@/features/vendor/pages/VendorSettingsPage";
import { renderWithProviders } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import { businessError, validationError } from "@/test/api-errors";
import type { VendorProfile, VendorProfileUpdateRequest } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

function setup(profile: VendorProfile = makeVendorProfile()) {
  mockFetch.mockResolvedValue(profile);
  return renderWithProviders(<VendorSettingsPage />);
}

async function waitForForm() {
  // The inputs render immediately with empty defaults; they are only populated
  // once the profile query resolves and re-seeds the form.
  await waitFor(() => {
    expect(screen.getByLabelText(/delivery radius/i)).toHaveValue(5);
  });
}

describe("VendorSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdate.mockResolvedValue(makeVendorProfile());
  });

  it("renders the current delivery settings", async () => {
    setup();
    await waitForForm();

    expect(screen.getByLabelText(/delivery radius/i)).toHaveValue(5);
    expect(screen.getByLabelText(/minimum order amount/i)).toHaveValue(300);
    expect(screen.getByLabelText(/base delivery fee/i)).toHaveValue(25);
    expect(screen.getByLabelText(/per km fee/i)).toHaveValue(5);
    expect(screen.getByLabelText(/free delivery above/i)).toHaveValue(1000);
    expect(screen.getByLabelText(/preparation time/i)).toHaveValue(30);
    expect(screen.getByLabelText(/slot duration/i)).toHaveValue(60);
    expect(screen.getByLabelText(/max orders per slot/i)).toHaveValue(10);
    expect(screen.getByLabelText(/accepting orders/i)).toBeChecked();
  });

  it("shows an unset optional threshold as blank rather than zero", async () => {
    setup(makeVendorProfile({ freeDeliveryAbove: null }));
    await waitForForm();

    expect(screen.getByLabelText(/free delivery above/i)).toHaveValue(null);
  });

  it("submits the delivery-settings update", async () => {
    setup();
    await waitForForm();

    const radius = screen.getByLabelText(/delivery radius/i);
    await userEvent.clear(radius);
    await userEvent.type(radius, "8.5");

    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });

    const payload = mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest;
    expect(payload.deliveryRadiusKm).toBe(8.5);
    // Business fields are resent untouched because this is one shared PUT.
    expect(payload.businessName).toBe("Petal & Stem");
    expect("hours" in payload).toBe(false);
  });

  it("sends a cleared optional threshold as null", async () => {
    setup();
    await waitForForm();

    await userEvent.clear(screen.getByLabelText(/free delivery above/i));
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });
    expect((mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest).freeDeliveryAbove).toBeNull();
  });

  it("can pause orders without suspending the account", async () => {
    setup();
    await waitForForm();

    await userEvent.click(screen.getByLabelText(/accepting orders/i));
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });
    expect((mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest).acceptingOrders).toBe(false);
  });

  it("rejects a zero delivery radius before calling the API", async () => {
    setup();
    await waitForForm();

    const radius = screen.getByLabelText(/delivery radius/i);
    await userEvent.clear(radius);
    await userEvent.type(radius, "0");
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/delivery radius must be greater than 0/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("rejects a negative fee", async () => {
    setup();
    await waitForForm();

    const fee = screen.getByLabelText(/base delivery fee/i);
    await userEvent.clear(fee);
    await userEvent.type(fee, "-5");
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/base delivery fee must be a number/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("rejects preparation time outside the backend range", async () => {
    setup();
    await waitForForm();

    const prep = screen.getByLabelText(/preparation time/i);
    await userEvent.clear(prep);
    await userEvent.type(prep, "5000");
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/preparation time must be between 1 and 1440/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("rejects a cleared required field", async () => {
    setup();
    await waitForForm();

    await userEvent.clear(screen.getByLabelText(/minimum order amount/i));
    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/minimum order amount is required/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("maps a backend field error onto the matching input", async () => {
    mockUpdate.mockRejectedValue(validationError({ maxOrdersPerSlot: "Max orders per slot must not exceed 10000" }));
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/must not exceed 10000/i)).toBeInTheDocument();
  });

  it("surfaces a non-field API error", async () => {
    mockUpdate.mockRejectedValue(businessError(409, "CONFLICT", "Settings conflict"));
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/settings conflict/i)).toBeInTheDocument();
  });

  it("confirms a successful save", async () => {
    setup();
    await waitForForm();

    await userEvent.click(screen.getByRole("button", { name: /save delivery settings/i }));

    expect(await screen.findByText(/delivery settings saved/i)).toBeInTheDocument();
  });

  it("reports a failed settings load", async () => {
    mockFetch.mockRejectedValue(businessError(403, "FORBIDDEN", "Forbidden"));

    renderWithProviders(<VendorSettingsPage />);

    expect(await screen.findByText(/does not have permission/i)).toBeInTheDocument();
  });
});
