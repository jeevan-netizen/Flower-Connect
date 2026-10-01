import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorProfilePage } from "@/features/vendor/pages/VendorProfilePage";
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
  return renderWithProviders(<VendorProfilePage />);
}

describe("VendorProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdate.mockResolvedValue(makeVendorProfile());
  });

  it("renders the vendor's current profile", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toHaveValue("Petal & Stem");
    });
    expect(screen.getByLabelText(/description/i)).toHaveValue("Seasonal bouquets");
    expect(screen.getByLabelText(/address line 1/i)).toHaveValue("12 MG Road");
    expect(screen.getByLabelText(/address line 2/i)).toHaveValue("Unit 3");
  });

  it("shows the service location as read-only data rather than an editable field", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByText(/indiranagar, bengaluru/i)).toBeInTheDocument();
    });
    expect(screen.queryByLabelText(/service location/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/latitude/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/longitude/i)).not.toBeInTheDocument();
  });

  it("does not expose an editable control for server-owned fields", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
    expect(screen.getByText("Approved")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /approval status/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /rating/i })).not.toBeInTheDocument();
  });

  it("submits the whole editable profile with the edited fields applied", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toHaveValue("Petal & Stem");
    });

    const businessName = screen.getByLabelText(/business name/i);
    await userEvent.clear(businessName);
    await userEvent.type(businessName, "Petal and Stem");

    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });

    const payload = mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest;
    expect(payload.businessName).toBe("Petal and Stem");
    // Untouched fields are resent so the PUT cannot reset them to a default.
    expect(payload.addressLine1).toBe("12 MG Road");
    expect(payload.serviceLocationId).toBe(3);
    expect(payload.deliveryRadiusKm).toBe(5);
    // The profile page does not own the hours sub-resource.
    expect("hours" in payload).toBe(false);
  });

  it("sends cleared optional fields as null", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/description/i)).toHaveValue("Seasonal bouquets");
    });

    await userEvent.clear(screen.getByLabelText(/description/i));
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });
    expect((mockUpdate.mock.calls[0][0] as VendorProfileUpdateRequest).description).toBeNull();
  });

  it("shows client-side validation for a cleared required field", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toHaveValue("Petal & Stem");
    });

    await userEvent.clear(screen.getByLabelText(/business name/i));
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/business name is required/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("keeps the user's input after a rejected submission", async () => {
    mockUpdate.mockRejectedValue(validationError({ businessName: "Business name is already taken" }));
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toHaveValue("Petal & Stem");
    });

    const businessName = screen.getByLabelText(/business name/i);
    await userEvent.clear(businessName);
    await userEvent.type(businessName, "A");
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/business name is already taken/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/business name/i)).toHaveValue("A");
  });

  it("does not show a validation error for a valid edit", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toHaveValue("Petal & Stem");
    });

    const businessName = screen.getByLabelText(/business name/i);
    await userEvent.clear(businessName);
    await userEvent.type(businessName, "A");
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText(/business name is required/i)).not.toBeInTheDocument();
  });

  it("rejects a logo URL that is neither absolute nor root-relative", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/logo url/i)).toBeInTheDocument();
    });

    await userEvent.type(screen.getByLabelText(/logo url/i), "not a url");
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/logo url must start with/i)).toBeInTheDocument();
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it("surfaces a backend field error on the matching input", async () => {
    mockUpdate.mockRejectedValue(validationError({ businessName: "Business name must not exceed 160 characters" }));
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/must not exceed 160 characters/i)).toBeInTheDocument();
    expect(screen.getByText(/some fields need attention/i)).toBeInTheDocument();
  });

  it("surfaces a non-field API error without a stack trace", async () => {
    mockUpdate.mockRejectedValue(businessError(409, "CONFLICT", "Business name already in use"));
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/business name already in use/i)).toBeInTheDocument();
  });

  it("confirms a successful save", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /save profile/i }));

    expect(await screen.findByText(/profile saved/i)).toBeInTheDocument();
  });

  it("reports a failed profile load and offers a retry", async () => {
    mockFetch.mockRejectedValue(businessError(404, "NOT_FOUND", "No vendor profile exists for this account"));

    renderWithProviders(<VendorProfilePage />);

    expect(await screen.findByText(/no vendor profile/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/business name/i)).not.toBeInTheDocument();
  });

  it("shows a loading state before the profile arrives", async () => {
    let resolveProfile: (value: VendorProfile) => void = () => {};
    mockFetch.mockImplementation(
      () => new Promise<VendorProfile>((resolve) => {
        resolveProfile = resolve;
      }),
    );

    renderWithProviders(<VendorProfilePage />);

    expect(await screen.findByText(/loading your profile/i)).toBeInTheDocument();

    resolveProfile(makeVendorProfile());

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
  });
});
