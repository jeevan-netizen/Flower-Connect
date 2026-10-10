import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorRegisterPage } from "@/features/vendor-registration/pages/VendorRegisterPage";
import { renderWithProviders } from "@/test/render";
import { makeServiceLocations, makeVendorProfile } from "@/test/factories";
import { businessError, validationError } from "@/test/api-errors";

const mockFetchServiceLocations = vi.hoisted(() => vi.fn());
const mockRegisterVendor = vi.hoisted(() => vi.fn());

// The locations API moved to the shared location slice in task 4.2 (the
// customer picker shares the endpoint); the registration client keeps only
// what is specific to it.
vi.mock("@/features/location/api", () => ({
  fetchServiceLocations: mockFetchServiceLocations,
}));

vi.mock("@/features/vendor-registration/api", () => ({
  registerVendor: mockRegisterVendor,
}));

/** Fills every field the DTO marks required, leaving the picker to each test. */
async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>, options?: { phone?: string }) {
  await user.type(screen.getByLabelText(/full name/i), "Petal Owner");
  await user.type(screen.getByLabelText(/email address/i), "petal@example.com");
  await user.type(screen.getByLabelText(/^password/i), "correct-horse");
  await user.type(screen.getByLabelText(/confirm password/i), "correct-horse");
  await user.type(screen.getByLabelText(/business name/i), "Petal & Stem");
  await user.type(screen.getByLabelText(/address line 1/i), "12 MG Road");
  if (options?.phone) {
    await user.type(screen.getByLabelText(/phone number/i), options.phone);
  }
}

describe("VendorRegisterPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
    mockRegisterVendor.mockResolvedValue({
      businessName: "Petal & Stem",
      status: "PENDING_APPROVAL",
      city: "Bengaluru",
      area: "Indiranagar",
      pincode: "560038",
    });
  });

  it("asks for the identity, business and location fields the DTO requires", async () => {
    renderWithProviders(<VendorRegisterPage />);

    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/address line 1/i)).toBeInTheDocument();
    expect(await screen.findByLabelText(/service area/i)).toBeInTheDocument();
  });

  it("never renders the password in clear text", async () => {
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/^password/i)).toHaveAttribute("type", "password");
    expect(screen.getByLabelText(/confirm password/i)).toHaveAttribute("type", "password");
  });

  it("blocks an empty submit and reports the required fields", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText(/full name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/business name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/address line 1 is required/i)).toBeInTheDocument();
    expect(screen.getByText(/select the service area/i)).toBeInTheDocument();
    expect(mockRegisterVendor).not.toHaveBeenCalled();
  });

  it("rejects a mismatched password before contacting the backend", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.clear(screen.getByLabelText(/confirm password/i));
    await user.type(screen.getByLabelText(/confirm password/i), "different");
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
    expect(mockRegisterVendor).not.toHaveBeenCalled();
  });

  it("rejects a phone number the DTO pattern would refuse", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user, { phone: "12345" });
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText(/valid phone number/i)).toBeInTheDocument();
    expect(mockRegisterVendor).not.toHaveBeenCalled();
  });

  it("offers every seeded service area, grouped by city, and submits the chosen id", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    const picker = await screen.findByLabelText(/service area/i);
    // The field is rendered before the data arrives, so wait for the options rather
    // than the label.
    expect(
      await within(picker).findByRole("option", { name: "Indiranagar (560038)" }),
    ).toHaveValue("3");
    expect(within(picker).getByRole("option", { name: "Koramangala (560034)" })).toHaveValue("4");
    expect(within(picker).getByRole("group", { name: "Bengaluru" })).toBeInTheDocument();

    await fillRequiredFields(user);
    await user.selectOptions(picker, "4");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    await waitFor(() => {
      expect(mockRegisterVendor).toHaveBeenCalledTimes(1);
    });
    expect(mockRegisterVendor).toHaveBeenCalledWith(
      expect.objectContaining({
        email: "petal@example.com",
        businessName: "Petal & Stem",
        addressLine1: "12 MG Road",
        serviceLocationId: 4,
      }),
    );
  });

  it("does not send optional delivery settings the vendor has not chosen yet", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    await waitFor(() => {
      expect(mockRegisterVendor).toHaveBeenCalled();
    });
    const payload = mockRegisterVendor.mock.calls[0]?.[0];
    expect(payload).not.toHaveProperty("deliveryRadiusKm");
    expect(payload).not.toHaveProperty("minOrderAmount");
    expect(payload).not.toHaveProperty("hours");
    expect(payload.phone).toBeNull();
  });

  it("reports the pending approval state and hands off to login for the vendor dashboard", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByRole("heading", { name: /application received/i })).toBeInTheDocument();
    // The status comes from the backend response, not a hard-coded assumption.
    expect(screen.getByText("PENDING_APPROVAL")).toBeInTheDocument();
    expect(screen.getByText(/Indiranagar, Bengaluru \(560038\)/)).toBeInTheDocument();

    const signIn = screen.getByRole("link", { name: /sign in to your vendor dashboard/i });
    expect(signIn).toHaveAttribute("href", "/login");
  });

  it("surfaces a backend validation failure on the matching input", async () => {
    const user = userEvent.setup();
    mockRegisterVendor.mockRejectedValue(
      validationError({ businessName: "Business name is required" }),
    );

    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText("Business name is required")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /application received/i })).not.toBeInTheDocument();
  });

  it("surfaces a duplicate-account conflict as a readable message", async () => {
    const user = userEvent.setup();
    mockRegisterVendor.mockRejectedValue(businessError(409, "CONFLICT", "Email already in use"));

    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText("Email already in use")).toBeInTheDocument();
  });

  describe("location loading states", () => {
    it("shows a loading state and disables the picker until the areas arrive", async () => {
      // Never resolves: the page must stay in its loading state, not an empty picker.
      mockFetchServiceLocations.mockReturnValue(new Promise(() => {}));

      renderWithProviders(<VendorRegisterPage />);

      expect(await screen.findByText(/loading service areas/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/service area/i)).toBeDisabled();
      expect(screen.getByText(/loading the available service areas/i)).toBeInTheDocument();
      // The button keeps its idle label: a permanently "Submitting..." button would be
      // a lie, since nothing is in flight.
      expect(screen.getByRole("button", { name: /submit application/i })).toBeEnabled();
    });

    it("refuses to register without a service area once the areas have failed to load", async () => {
      const user = userEvent.setup();
      mockFetchServiceLocations.mockRejectedValue(businessError(500, "INTERNAL_ERROR", "Boom"));

      renderWithProviders(<VendorRegisterPage />);

      expect(await screen.findByText(/could not load the service areas/i)).toBeInTheDocument();
      // Every other field is valid, so the missing service area is the only reason
      // this can fail — which is exactly what the field-level message must explain.
      await fillRequiredFields(user);
      await user.click(screen.getByRole("button", { name: /submit application/i }));

      expect(await screen.findByText(/select the service area/i)).toBeInTheDocument();
      expect(mockRegisterVendor).not.toHaveBeenCalled();
    });

    it("explains a failed location fetch and can retry it", async () => {
      const user = userEvent.setup();
      mockFetchServiceLocations
        .mockRejectedValueOnce(businessError(500, "INTERNAL_ERROR", "Boom"))
        .mockResolvedValueOnce(makeServiceLocations());

      renderWithProviders(<VendorRegisterPage />);

      expect(await screen.findByText(/could not load the service areas/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/service area/i)).toBeDisabled();

      await user.click(screen.getByRole("button", { name: /try again/i }));

      await waitFor(() => {
        expect(screen.getByLabelText(/service area/i)).toBeEnabled();
      });
      expect(mockFetchServiceLocations).toHaveBeenCalledTimes(2);
      expect(screen.getByRole("option", { name: "Indiranagar (560038)" })).toBeInTheDocument();
    });

    it("reports an empty region instead of offering an unusable picker", async () => {
      mockFetchServiceLocations.mockResolvedValue([]);

      renderWithProviders(<VendorRegisterPage />);

      expect(await screen.findByText(/no service areas are configured yet/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/service area/i)).toBeDisabled();
      expect(within(screen.getByLabelText(/service area/i)).getAllByRole("option")).toHaveLength(1);
    });
  });

  it("exposes no admin or vendor-management entry point", async () => {
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: /admin/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /vendors/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /users/i })).not.toBeInTheDocument();
  });

  it("links onward to login and to customer registration, not to an admin area", async () => {
    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /sign in/i })).toHaveAttribute("href", "/login");
    expect(screen.getByRole("link", { name: /create a customer account/i })).toHaveAttribute(
      "href",
      "/register",
    );
  });

  it("treats the seeded profile the backend returns as authoritative", async () => {
    const user = userEvent.setup();
    mockRegisterVendor.mockResolvedValue({
      businessName: "Renamed Shop",
      status: "PENDING_APPROVAL",
      city: "Bengaluru",
      area: "Koramangala",
      pincode: "560034",
    });

    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByText(/Renamed Shop/)).toBeInTheDocument();
    expect(screen.getByText(/Koramangala, Bengaluru \(560034\)/)).toBeInTheDocument();
  });

  it("does not invent a vendor profile for a response missing the fields it renders", async () => {
    const user = userEvent.setup();
    // A profile the factory would normally fill in is irrelevant here: the page must
    // render exactly what the endpoint returned.
    mockRegisterVendor.mockResolvedValue(makeVendorProfile());

    renderWithProviders(<VendorRegisterPage />);

    await waitFor(() => {
      expect(screen.getByLabelText(/service area/i)).toBeInTheDocument();
    });
    await fillRequiredFields(user);
    await user.selectOptions(screen.getByLabelText(/service area/i), "3");
    await user.click(screen.getByRole("button", { name: /submit application/i }));

    expect(await screen.findByRole("heading", { name: /application received/i })).toBeInTheDocument();
    // `registerVendor` narrows the response, so a raw profile must not leak its
    // owner email or coordinates into the success panel.
    expect(screen.queryByText(/petal@example\.com/)).not.toBeInTheDocument();
    expect(screen.queryByText(/12\.97/)).not.toBeInTheDocument();
  });
});