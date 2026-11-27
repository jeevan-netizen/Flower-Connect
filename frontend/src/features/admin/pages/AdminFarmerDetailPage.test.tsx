import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { AdminFarmerDetailPage } from "@/features/admin/pages/AdminFarmerDetailPage";
import { renderWithProviders } from "@/test/render";
import { businessError } from "@/test/api-errors";
import type { AdminFarmerDetail } from "@/features/admin/types";
import type { PassbookEntry } from "@/features/farmer/types";

const mockFetchFarmerDetail = vi.hoisted(() => vi.fn());
const mockUpdateStatus = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminFarmerDetail: mockFetchFarmerDetail,
  updateFarmerStatus: mockUpdateStatus,
}));

const makeEntry = (overrides: Partial<PassbookEntry> = {}): PassbookEntry =>
  ({
    id: 101,
    type: "DEPOSIT",
    date: "2026-09-05T10:00:00",
    amount: 1000,
    runningBalance: 1000,
    mode: null,
    ...overrides,
  } as PassbookEntry);

const makeDetail = (overrides: Partial<AdminFarmerDetail> = {}): AdminFarmerDetail => ({
  id: 42,
  email: "farmer@example.com",
  fullName: "Farm Owner",
  phone: "+919999999999",
  address: "123 Garden Lane",
  status: "ACTIVE" as const,
  createdAt: "2026-09-02T09:15:00",
  linkedVendors: [],
  ...overrides,
});

function setup(detail = makeDetail()) {
  mockFetchFarmerDetail.mockResolvedValue(detail);
  return renderWithProviders(
    <Routes>
      <Route path="/admin/farmers/:farmerId" element={<AdminFarmerDetailPage />} />
    </Routes>,
    { route: "/admin/farmers/42" },
  );
}

async function openStatusDialog(action: RegExp) {
  await userEvent.click(screen.getByRole("button", { name: action }));
  return screen.findByRole("dialog");
}

describe("AdminFarmerDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateStatus.mockResolvedValue(makeDetail({ status: "SUSPENDED" }));
  });

  it("renders the farmer's profile including address", async () => {
    setup();
    await waitFor(() => screen.getByText("Farm Owner"));

    expect(screen.getByText("farmer@example.com")).toBeInTheDocument();
    expect(screen.getByText("+919999999999")).toBeInTheDocument();
    expect(screen.getByText("123 Garden Lane")).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("omits the address when it is not recorded", async () => {
    setup(makeDetail({ address: null }));
    await waitFor(() => screen.getByText("Farm Owner"));

    expect(screen.queryByText("123 Garden Lane")).not.toBeInTheDocument();
  });

  it("shows the farmer's account status and registration date", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText("Active", { selector: "dd" })).toBeInTheDocument(),
    );
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("shows each linked florist's ledger overview with entries and running balance", async () => {
    setup(
      makeDetail({
        linkedVendors: [
          {
            vendorId: 7,
            vendorName: "Green Acres",
            linkStatus: "ACTIVE",
            linkCreatedAt: "2026-08-01T00:00:00",
            outstandingBalance: 1500,
            entries: [
              makeEntry({ type: "DEPOSIT", amount: 1000, runningBalance: 1000 }),
              makeEntry({
                id: 102,
                type: "COLLECTION",
                amount: 400,
                runningBalance: 600,
              }),
            ],
          },
        ],
      }),
    );

    await waitFor(() => screen.getByText("Green Acres"));
    expect(screen.getByText("₹1,500.00")).toBeInTheDocument();
    expect(screen.getByText("Flower deposit")).toBeInTheDocument();
    expect(screen.getByText("Money collection")).toBeInTheDocument();
    expect(screen.getByText("₹400.00")).toBeInTheDocument();
    expect(screen.getByText("₹600.00")).toBeInTheDocument();
  });

  it("renders an empty ledger when the farmer has no linked florists", async () => {
    setup(makeDetail({ linkedVendors: [] }));
    await waitFor(() => screen.getByText("Farm Owner"));

    await waitFor(() => {
      expect(screen.getByText(/no linked florists yet/i)).toBeInTheDocument();
    });
  });

  it("shows a loading state before the detail resolves", async () => {
    mockFetchFarmerDetail.mockResolvedValueOnce(
      new Promise<AdminFarmerDetail>((resolve) =>
        setTimeout(() => resolve(makeDetail()), 0),
      ),
    );
    renderWithProviders(
      <Routes>
        <Route path="/admin/farmers/:farmerId" element={<AdminFarmerDetailPage />} />
      </Routes>,
      { route: "/admin/farmers/42" },
    );

    expect(screen.getByText(/loading farmer/i)).toBeInTheDocument();
    await waitFor(() => screen.getByText("Farm Owner"));
  });

  it("reports a failed detail read and offers a retry", async () => {
    mockFetchFarmerDetail.mockRejectedValue(businessError(500, "VALIDATION_FAILED", "Database unavailable"));
    renderWithProviders(
      <Routes>
        <Route path="/admin/farmers/:farmerId" element={<AdminFarmerDetailPage />} />
      </Routes>,
      { route: "/admin/farmers/42" },
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("reports a 403 detail read as a permission problem", async () => {
    mockFetchFarmerDetail.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(
      <Routes>
        <Route path="/admin/farmers/:farmerId" element={<AdminFarmerDetailPage />} />
      </Routes>,
      { route: "/admin/farmers/42" },
    );

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  it("reactivates a suspended farmer with a reason", async () => {
    setup(makeDetail({ status: "SUSPENDED" }));
    await waitFor(() =>
      expect(screen.getByText("Suspended", { selector: "dd" })).toBeInTheDocument(),
    );

    const dialog = await openStatusDialog(/^reactivate$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Appeal upheld");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to active/i }));

    await waitFor(() =>
      expect(mockUpdateStatus).toHaveBeenCalledWith(42, {
        status: "ACTIVE",
        reason: "Appeal upheld",
      }),
    );
    await waitFor(() => {
      expect(screen.getByText(/farm owner is now active/i)).toBeInTheDocument();
    });
  });

  it("requires a reason for suspension", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText("Active", { selector: "dd" })).toBeInTheDocument(),
    );

    const dialog = await openStatusDialog(/^suspend$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    expect(await within(dialog).findByText("Reason is required")).toBeInTheDocument();
    expect(mockUpdateStatus).not.toHaveBeenCalled();
  });

  it("closes the dialog without acting when cancelled", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText("Active", { selector: "dd" })).toBeInTheDocument(),
    );

    const dialog = await openStatusDialog(/^suspend$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /cancel/i }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockUpdateStatus).not.toHaveBeenCalled();
  });
});
