import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminFarmersPage } from "@/features/admin/pages/AdminFarmersPage";
import { renderWithProviders } from "@/test/render";
import { makePage } from "@/test/factories";
import { businessError } from "@/test/api-errors";
import type { AdminFarmer } from "@/features/admin/types";

const mockFetchFarmers = vi.hoisted(() => vi.fn());
const mockUpdateStatus = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminFarmers: mockFetchFarmers,
  updateFarmerStatus: mockUpdateStatus,
}));

const makeFarmer = (overrides: Partial<AdminFarmer> = {}): AdminFarmer => ({
  id: 42,
  email: "farmer@example.com",
  fullName: "Farm Owner",
  phone: "+919999999999",
  status: "ACTIVE" as const,
  createdAt: "2026-09-02T09:15:00",
  ...overrides,
});

function setup(page = makePage<AdminFarmer>([makeFarmer()])) {
  mockFetchFarmers.mockResolvedValue(page);
  return renderWithProviders(<AdminFarmersPage />);
}

async function waitForRows() {
  await screen.findByRole("table");
}

async function openStatusDialog(action: RegExp) {
  await userEvent.click(screen.getByRole("button", { name: action }));
  return screen.findByRole("dialog");
}

describe("AdminFarmersPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateStatus.mockResolvedValue(
      makeFarmer({ status: "SUSPENDED" }),
    );
  });

  it("renders each farmer's name, email, mobile, status and registration date", async () => {
    setup();
    await waitForRows();

    expect(screen.getByText("Farm Owner")).toBeInTheDocument();
    expect(screen.getByText("farmer@example.com")).toBeInTheDocument();
    expect(screen.getByText("+919999999999")).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("shows a loading state before the listing resolves", () => {
    setup();

    expect(screen.getByText(/loading farmers/i)).toBeInTheDocument();
  });

  it("shows an empty state when no farmer matches the filters", async () => {
    setup(makePage<AdminFarmer>([]));
    await userEvent.type(screen.getByLabelText(/^search$/i), "nonexistent");

    await waitFor(() => {
      expect(screen.getByText(/no farmer accounts match these filters/i)).toBeInTheDocument();
    });
  });

  it("reports a failed listing and offers a retry", async () => {
    mockFetchFarmers.mockRejectedValue(businessError(500, "VALIDATION_FAILED", "Database unavailable"));
    renderWithProviders(<AdminFarmersPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("reports a 403 listing as a permission problem", async () => {
    mockFetchFarmers.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(<AdminFarmersPage />);

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  it("offers suspend for an active farmer and reactivate for a suspended or disabled farmer", async () => {
    setup(
      makePage<AdminFarmer>([
        makeFarmer({ id: 42, status: "ACTIVE" }),
        makeFarmer({ id: 43, email: "paused@example.com", status: "SUSPENDED" }),
        makeFarmer({ id: 44, email: "off@example.com", status: "DISABLED" }),
      ]),
    );
    await waitForRows();

    const rows = screen.getAllByRole("row").slice(1);

    expect(within(rows[0]!).getByRole("button", { name: /^suspend$/i })).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole("button", { name: /^reactivate$/i })).not.toBeInTheDocument();

    expect(within(rows[1]!).getByRole("button", { name: /^reactivate$/i })).toBeInTheDocument();
    expect(within(rows[2]!).getByRole("button", { name: /^reactivate$/i })).toBeInTheDocument();
  });

  it("requires a reason for every status change", async () => {
    setup(makePage<AdminFarmer>([makeFarmer({ status: "SUSPENDED" })]));
    await waitForRows();

    const dialog = await openStatusDialog(/^reactivate$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /set to active/i }));

    expect(await within(dialog).findByText("Reason is required")).toBeInTheDocument();
    expect(mockUpdateStatus).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Appeal upheld");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to active/i }));

    await waitFor(() =>
      expect(mockUpdateStatus).toHaveBeenCalledWith(42, {
        status: "ACTIVE",
        reason: "Appeal upheld",
      }),
    );
  });

  it("suspends an active farmer with a reason", async () => {
    setup();
    await waitForRows();

    const dialog = await openStatusDialog(/^suspend$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Chargeback fraud");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() =>
      expect(mockUpdateStatus).toHaveBeenCalledWith(42, {
        status: "SUSPENDED",
        reason: "Chargeback fraud",
      }),
    );
    await waitFor(() => {
      expect(screen.getByText(/farm owner is now suspended/i)).toBeInTheDocument();
    });
  });

  it("keeps the dialog open and shows the backend message on a 404", async () => {
    setup();
    await waitForRows();
    mockUpdateStatus.mockRejectedValue(businessError(404, "NOT_FOUND", "Farmer not found"));

    const dialog = await openStatusDialog(/^suspend$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Testing not found");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() => {
      expect(within(dialog).getByText("Farmer not found")).toBeInTheDocument();
    });
  });

  it("searches by name, email or mobile", async () => {
    setup();
    await waitForRows();

    await userEvent.type(screen.getByLabelText(/^search$/i), "rose");

    await waitFor(() => {
      expect(mockFetchFarmers).toHaveBeenLastCalledWith({
        search: "rose",
        status: null,
        page: 0,
      });
    });
  });

  it("filters by status and resets to the first page", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^account status$/i), "SUSPENDED");

    await waitFor(() => {
      expect(mockFetchFarmers).toHaveBeenLastCalledWith({
        search: "",
        status: "SUSPENDED",
        page: 0,
      });
    });
  });

  it("keeps the active filters when paging", async () => {
    setup(
      makePage<AdminFarmer>([makeFarmer()], {
        totalElements: 40,
        totalPages: 2,
        first: true,
        last: false,
      }),
    );
    await waitForRows();

    await userEvent.type(screen.getByLabelText(/^search$/i), "lily");
    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchFarmers).toHaveBeenLastCalledWith({
        search: "lily",
        status: null,
        page: 1,
      });
    });
  });

  it("requests the next page with the zero-based index the backend expects", async () => {
    setup(
      makePage<AdminFarmer>([makeFarmer()], {
        totalElements: 40,
        totalPages: 2,
        first: true,
        last: false,
      }),
    );
    await waitForRows();

    expect(screen.getByText(/page 1 of 2/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchFarmers).toHaveBeenLastCalledWith({
        search: "",
        status: null,
        page: 1,
      });
    });
  });

  it("keeps the active filters when paging", async () => {
    setup(
      makePage<AdminFarmer>([makeFarmer()], {
        totalElements: 40,
        totalPages: 2,
        first: true,
        last: false,
      }),
    );
    await waitForRows();

    await userEvent.type(screen.getByLabelText(/^search$/i), "lily");
    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchFarmers).toHaveBeenLastCalledWith({
        search: "lily",
        status: null,
        page: 1,
      });
    });
  });

  it("closes the dialog without acting when cancelled", async () => {
    setup();
    await waitForRows();

    const dialog = await openStatusDialog(/^suspend$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /cancel/i }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockUpdateStatus).not.toHaveBeenCalled();
  });
});
