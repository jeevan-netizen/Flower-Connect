import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomerProfilePage } from "@/features/customer/pages/CustomerProfilePage";
import { renderWithProviders } from "@/test/render";
import {
  makeUserResponse,
  makeAddressResponse,
  makeAddressPageResponse,
  makeServiceLocations,
} from "@/test/factories";
import { validationError, businessError } from "@/test/api-errors";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import type { AddressPageResponse } from "@/features/checkout/types";

const mockUpdateCurrentUser = vi.hoisted(() => vi.fn());
const mockLogout = vi.hoisted(() => vi.fn());
const mockFetchAddresses = vi.hoisted(() => vi.fn());
const mockCreateAddress = vi.hoisted(() => vi.fn());
const mockUpdateAddress = vi.hoisted(() => vi.fn());
const mockDeleteAddress = vi.hoisted(() => vi.fn());
const mockUseServiceLocations = vi.hoisted(() => vi.fn());

vi.mock("@/features/auth/api", () => ({
  updateCurrentUser: mockUpdateCurrentUser,
  logout: mockLogout,
}));
vi.mock("@/features/checkout/api", () => ({
  fetchAddresses: mockFetchAddresses,
}));
vi.mock("@/features/customer/address/api", () => ({
  createAddress: mockCreateAddress,
  updateAddress: mockUpdateAddress,
  deleteAddress: mockDeleteAddress,
}));
vi.mock("@/features/location/queries", () => ({
  useServiceLocations: mockUseServiceLocations,
}));

const serviceLocations = makeServiceLocations();

function setup(
  user = makeUserResponse(),
  addresses: AddressPageResponse | "error" = makeAddressPageResponse(),
) {
  useAuthStore.setState({ user });
  mockUseServiceLocations.mockReturnValue({
    data: serviceLocations,
    isPending: false,
    error: undefined,
  });
  mockUpdateCurrentUser.mockResolvedValue(user);
  mockCreateAddress.mockResolvedValue(makeAddressResponse());
  mockUpdateAddress.mockResolvedValue(makeAddressResponse());
  mockDeleteAddress.mockResolvedValue(undefined);
  mockLogout.mockResolvedValue(undefined);

  if (addresses === "error") {
    mockFetchAddresses.mockRejectedValue(
      businessError(500, "INTERNAL_SERVER_ERROR", "boom"),
    );
  } else {
    mockFetchAddresses.mockResolvedValue(addresses);
  }

  return renderWithProviders(<CustomerProfilePage />);
}

describe("CustomerProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchAddresses.mockResolvedValue(makeAddressPageResponse());
  });

  it("renders the customer's account details", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });
    expect(screen.getByText(/buyer@example.com/i)).toBeInTheDocument();
    expect(screen.getByText("CUSTOMER")).toBeInTheDocument();
    expect(screen.getByText(/42/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/phone number/i)).toHaveValue("+919876543210");
  });

  it("shows editable full name and phone in the account section", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });
    expect(screen.getByLabelText(/phone number/i)).toHaveValue("+919876543210");
    expect(screen.getByRole("button", { name: /save account details/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /change password/i })).toBeInTheDocument();
  });

  it("saves edited account details via PATCH /users/me", async () => {
    const updated = makeUserResponse({ fullName: "Bea Updated", phone: "+919999999999" });
    mockUpdateCurrentUser.mockResolvedValue(updated);
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });

    const fullName = screen.getByLabelText(/full name/i);
    await userEvent.clear(fullName);
    await userEvent.type(fullName, "Bea Updated");

    const phone = screen.getByLabelText(/phone number/i);
    await userEvent.clear(phone);
    await userEvent.type(phone, "+919999999999");

    await userEvent.click(screen.getByRole("button", { name: /save account details/i }));

    await waitFor(() => {
      expect(mockUpdateCurrentUser).toHaveBeenCalledTimes(1);
    });
    const payload = mockUpdateCurrentUser.mock.calls[0][0];
    expect(payload).toEqual({ fullName: "Bea Updated", phone: "+919999999999" });

    await waitFor(() => {
      expect(screen.getByText(/account details saved/i)).toBeInTheDocument();
    });
  });

  it("shows client-side validation when full name is cleared", async () => {
    setup();

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });

    const fullName = screen.getByLabelText(/full name/i);
    await userEvent.clear(fullName);
    await userEvent.click(screen.getByRole("button", { name: /save account details/i }));

    expect(await screen.findByText(/full name is required/i)).toBeInTheDocument();
    expect(mockUpdateCurrentUser).not.toHaveBeenCalled();
  });

  it("shows a backend field error on the matching account input", async () => {
    setup();
    mockUpdateCurrentUser.mockRejectedValue(
      validationError({ fullName: "Full name must not exceed 128 characters" }),
    );

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });
    await userEvent.click(screen.getByRole("button", { name: /save account details/i }));

    expect(await screen.findByText(/must not exceed 128 characters/i)).toBeInTheDocument();
  });

  it("shows a non-field API error without a stack trace", async () => {
    setup();
    mockUpdateCurrentUser.mockRejectedValue(businessError(409, "CONFLICT", "Phone already in use"));

    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toHaveValue("Bea Buyer");
    });
    await userEvent.click(screen.getByRole("button", { name: /save account details/i }));

    const messages = await screen.findAllByText(/phone already in use/i);
    expect(messages.length).toBeGreaterThan(0);
  });

  it("renders the saved addresses with the default address marked", async () => {
    const defaultAddress = makeAddressResponse({ defaultAddress: true });
    const nonDefault = makeAddressResponse({ id: 43, defaultAddress: false });
    setup(makeUserResponse(), makeAddressPageResponse({ content: [defaultAddress, nonDefault] }));

    await waitFor(() => {
      expect(screen.getByTestId(`address-row-${defaultAddress.id}`)).toBeInTheDocument();
    });
    const defaultRow = screen.getByTestId(`address-row-${defaultAddress.id}`);
    expect(within(defaultRow).getByText(/default/i)).toBeInTheDocument();
    expect(screen.getByTestId(`address-row-${nonDefault.id}`)).toBeInTheDocument();
  });

  it("adds a new address via POST /addresses", async () => {
    const created = makeAddressResponse({ id: 100, label: "Office" });
    mockCreateAddress.mockResolvedValue(created);
    setup();

    await userEvent.click(screen.getByRole("button", { name: /add address/i }));

    await waitFor(() => {
      expect(screen.getByLabelText(/label/i)).toBeInTheDocument();
    });

    await userEvent.type(screen.getByLabelText(/label/i), "Office");
    await userEvent.type(screen.getByLabelText(/address line 1/i), "45 MG Road");
    await userEvent.selectOptions(screen.getByLabelText(/service area/i), "3");

    await userEvent.click(screen.getByRole("button", { name: /save address/i }));

    await waitFor(() => {
      expect(mockCreateAddress).toHaveBeenCalledTimes(1);
    });
    const payload = mockCreateAddress.mock.calls[0][0];
    expect(payload.label).toBe("Office");
    expect(payload.line1).toBe("45 MG Road");
    expect(payload.serviceLocationId).toBe(3);
  });

  it("edits an existing address via PUT /addresses/{id}", async () => {
    const address = makeAddressResponse({ id: 42, label: "Home" });
    setup(makeUserResponse(), makeAddressPageResponse({ content: [address] }));

    await waitFor(() => {
      expect(screen.getByTestId(`address-row-${address.id}`)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /edit/i }));

    await waitFor(() => {
      expect(screen.getByLabelText(/label/i)).toHaveValue("Home");
    });

    const label = screen.getByLabelText(/label/i);
    await userEvent.clear(label);
    await userEvent.type(label, "Home Updated");

    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(mockUpdateAddress).toHaveBeenCalledTimes(1);
    });
    const [id, payload] = mockUpdateAddress.mock.calls[0];
    expect(id).toBe(42);
    expect(payload.label).toBe("Home Updated");
  });

  it("deletes an address via DELETE /addresses/{id}", async () => {
    const address = makeAddressResponse({ id: 42 });
    setup(makeUserResponse(), makeAddressPageResponse({ content: [address] }));

    vi.spyOn(window, "confirm").mockReturnValue(true);

    await waitFor(() => {
      expect(screen.getByTestId(`address-row-${address.id}`)).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /delete/i }));

    await waitFor(() => {
      expect(mockDeleteAddress).toHaveBeenCalledWith(42);
    });
  });

  it("shows a loading state while addresses are fetched", async () => {
    mockFetchAddresses.mockImplementation(() => new Promise(() => {}));
    setup();

    expect(await screen.findByText(/loading your addresses/i)).toBeInTheDocument();
  });

  it("shows an error state when addresses fail to load", async () => {
    setup(makeUserResponse(), "error");

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
  });

  it("calls logout and clears the address cache on Log out", async () => {
    const { queryClient } = setup();

    // Prime the cache so we can observe it being cleared.
    queryClient.setQueryData(["profile", "addresses", "list", 0, 50], makeAddressPageResponse());

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /log out/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /log out/i }));

    await waitFor(() => {
      expect(mockLogout).toHaveBeenCalledTimes(1);
    });
  });
});
