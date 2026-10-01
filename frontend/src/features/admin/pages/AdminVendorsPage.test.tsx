import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, Route, Routes } from "react-router-dom";
import { AdminVendorsPage } from "@/features/admin/pages/AdminVendorsPage";
import { renderWithProviders } from "@/test/render";
import { makePage, makeVendorProfile } from "@/test/factories";
import { businessError, validationError } from "@/test/api-errors";
import type { VendorProfile } from "@/features/vendor/types";

const mockFetchVendors = vi.hoisted(() => vi.fn());
const mockApprove = vi.hoisted(() => vi.fn());
const mockReject = vi.hoisted(() => vi.fn());
const mockSuspend = vi.hoisted(() => vi.fn());
const mockReinstate = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminVendors: mockFetchVendors,
  approveVendor: mockApprove,
  rejectVendor: mockReject,
  suspendVendor: mockSuspend,
  reinstateVendor: mockReinstate,
}));

function setup(page = makePage<VendorProfile>([makeVendorProfile({ status: "PENDING_APPROVAL" })])) {
  mockFetchVendors.mockResolvedValue(page);
  return renderWithProviders(<AdminVendorsPage />);
}

/** The table renders only once a non-empty page of vendors has loaded. */
async function waitForRows() {
  await screen.findByRole("table");
}

/** Opens the confirmation dialog for `action` on the single rendered row. */
async function openDialog(action: RegExp) {
  await userEvent.click(screen.getByRole("button", { name: action }));
  return screen.findByRole("dialog");
}

describe("AdminVendorsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApprove.mockResolvedValue(makeVendorProfile({ status: "APPROVED" }));
    mockReject.mockResolvedValue(makeVendorProfile({ status: "REJECTED" }));
    mockSuspend.mockResolvedValue(makeVendorProfile({ status: "SUSPENDED" }));
    mockReinstate.mockResolvedValue(makeVendorProfile({ status: "APPROVED" }));
  });

  it("renders the vendor details an administrator needs to decide", async () => {
    setup();
    await waitForRows();

    await waitFor(() => {
      expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    });
    expect(screen.getByText("petal@example.com")).toBeInTheDocument();
    expect(screen.getByText(/Indiranagar, Bengaluru/)).toBeInTheDocument();
    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
  });

  it("shows a loading state before the listing resolves", () => {
    setup();

    expect(screen.getByText(/loading vendors/i)).toBeInTheDocument();
  });

  it("shows an empty state when no vendor matches", async () => {
    setup(makePage<VendorProfile>([]));
    await waitFor(() => {
      expect(screen.getByText(/no vendor profiles exist yet/i)).toBeInTheDocument();
    });
  });

  it("reports a failed listing with the backend message and offers a retry", async () => {
    mockFetchVendors.mockRejectedValue(businessError(500, "VALIDATION_FAILED", "Database unavailable"));
    renderWithProviders(<AdminVendorsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("reports a 403 from the listing as a permission problem, not a generic failure", async () => {
    mockFetchVendors.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(<AdminVendorsPage />);

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  it("offers only the transitions the backend permits for the current status", async () => {
    setup(makePage<VendorProfile>([
      makeVendorProfile({ id: 1, businessName: "Pending Shop", status: "PENDING_APPROVAL" }),
      makeVendorProfile({ id: 2, businessName: "Live Shop", status: "APPROVED" }),
      makeVendorProfile({ id: 3, businessName: "Paused Shop", status: "SUSPENDED" }),
      makeVendorProfile({ id: 4, businessName: "Refused Shop", status: "REJECTED" }),
    ]));
    await waitForRows();

    const rows = screen.getAllByRole("row").slice(1);

    expect(within(rows[0]!).getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(within(rows[0]!).getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole("button", { name: "Suspend" })).not.toBeInTheDocument();

    expect(within(rows[1]!).getByRole("button", { name: "Suspend" })).toBeInTheDocument();
    expect(within(rows[1]!).queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();

    expect(within(rows[2]!).getByRole("button", { name: "Reinstate" })).toBeInTheDocument();

    // A rejected profile has no route back in the backend, so no button is offered.
    expect(within(rows[3]!).getByText(/no further action available/i)).toBeInTheDocument();
    expect(within(rows[3]!).queryByRole("button")).not.toBeInTheDocument();
  });

  it("approves without asking for a reason", async () => {
    setup();
    await waitForRows();

    const dialog = await openDialog(/^approve$/i);
    expect(within(dialog).queryByLabelText(/reason/i)).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(mockApprove).toHaveBeenCalledWith(7));
    expect(mockReject).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByText(/approved petal & stem/i)).toBeInTheDocument();
    });
  });

  it("requires a reason before rejecting", async () => {
    setup();
    await waitForRows();

    const dialog = await openDialog(/^reject$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    expect(await within(dialog).findByText("Reason is required")).toBeInTheDocument();
    expect(mockReject).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Missing trade licence");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    await waitFor(() =>
      expect(mockReject).toHaveBeenCalledWith(7, { reason: "Missing trade licence" }),
    );
  });

  it("rejects a reason longer than the backend allows, before sending it", async () => {
    setup();
    await waitForRows();

    const dialog = await openDialog(/^reject$/i);
    await userEvent.type(
      within(dialog).getByLabelText(/reason/i),
      "x".repeat(501),
    );
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    expect(
      await within(dialog).findByText("Reason must not exceed 500 characters"),
    ).toBeInTheDocument();
    expect(mockReject).not.toHaveBeenCalled();
  });

  it("suspends an approved vendor with a reason", async () => {
    setup(makePage<VendorProfile>([makeVendorProfile({ status: "APPROVED" })]));
    await waitForRows();

    const dialog = await openDialog(/^suspend$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Repeated late deliveries");
    await userEvent.click(within(dialog).getByRole("button", { name: "Suspend" }));

    await waitFor(() =>
      expect(mockSuspend).toHaveBeenCalledWith(7, { reason: "Repeated late deliveries" }),
    );
  });

  it("reinstates a suspended vendor without asking for a reason", async () => {
    setup(makePage<VendorProfile>([makeVendorProfile({ status: "SUSPENDED" })]));
    await waitForRows();

    const dialog = await openDialog(/^reinstate$/i);
    expect(within(dialog).queryByLabelText(/reason/i)).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Reinstate" }));

    await waitFor(() => expect(mockReinstate).toHaveBeenCalledWith(7));
    expect(mockApprove).not.toHaveBeenCalled();
  });

  it("keeps the dialog open and shows the backend message when a transition conflicts", async () => {
    setup();
    await waitForRows();
    mockApprove.mockRejectedValue(
      businessError(409, "CONFLICT", "Cannot approve a vendor profile in status APPROVED"),
    );

    const dialog = await openDialog(/^approve$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: "Approve" }));

    await waitFor(() => {
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "Cannot approve a vendor profile in status APPROVED",
      );
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("shows a backend field-level validation message on the reason input", async () => {
    setup();
    await waitForRows();
    mockReject.mockRejectedValue(validationError({ reason: "Reason is required" }));

    const dialog = await openDialog(/^reject$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "placeholder");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    await waitFor(() => {
      expect(within(dialog).getByText("Reason is required")).toBeInTheDocument();
    });
  });

  it("does not send a second transition while one is in flight", async () => {
    setup(
      makePage<VendorProfile>([
        makeVendorProfile({ id: 1, businessName: "Pending Shop", status: "PENDING_APPROVAL" }),
      ]),
    );
    await waitForRows();

    let resolveApprove: (value: VendorProfile) => void = () => {};
    mockApprove.mockImplementation(
      () =>
        new Promise<VendorProfile>((resolve) => {
          resolveApprove = resolve;
        }),
    );

    const dialog = await openDialog(/^approve$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: "Approve" }));

    // The confirm button is disabled while the mutation is pending, so a second
    // click cannot fire the same transition twice.
    await waitFor(() => {
      expect(within(screen.getByRole("dialog")).getByRole("button", { name: "Working..." })).toBeDisabled();
    });
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Working..." }));
    expect(mockApprove).toHaveBeenCalledTimes(1);

    resolveApprove(makeVendorProfile({ status: "APPROVED" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("filters by status and returns to the first page when the filter changes", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/approval status/i), "APPROVED");

    await waitFor(() => {
      expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: "APPROVED", page: 0 });
    });
  });

  it("omits the status parameter when the filter is cleared back to all", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/approval status/i), "SUSPENDED");
    await waitFor(() => expect(mockFetchVendors).toHaveBeenCalledTimes(2));
    await userEvent.selectOptions(screen.getByLabelText(/approval status/i), "");

    await waitFor(() => {
      expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: null, page: 0 });
    });
  });

  it("requests the next page with the zero-based index the backend expects", async () => {
    setup(
      makePage<VendorProfile>([makeVendorProfile()], {
        totalElements: 25,
        totalPages: 2,
        first: true,
        last: false,
      }),
    );
    await waitForRows();

    expect(screen.getByText(/page 1 of 2/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: null, page: 1 });
    });
  });

  /**
   * The admin dashboard deep-links into this page as
   * `/admin/vendors?status=PENDING_APPROVAL` ("View all N pending
   * applications"). Without reading the query string that link would silently
   * open the unfiltered list.
   */
  describe("status filter seeded from the query string", () => {
    function renderAtRoute(route: string) {
      mockFetchVendors.mockResolvedValue(
        makePage<VendorProfile>([makeVendorProfile({ status: "PENDING_APPROVAL" })]),
      );
      return renderWithProviders(<AdminVendorsPage />, { route });
    }

    it("starts filtered when the dashboard link is followed", async () => {
      renderAtRoute("/admin/vendors?status=PENDING_APPROVAL");

      await waitFor(() => {
        expect(mockFetchVendors).toHaveBeenCalledWith({ status: "PENDING_APPROVAL", page: 0 });
      });
      // The applied filter is asserted on the control itself, not only on the
      // outgoing request, so a page that queried correctly but rendered the
      // wrong selection would still fail.
      await waitFor(() => {
        expect(screen.getByLabelText(/approval status/i)).toHaveValue("PENDING_APPROVAL");
      });
    });

    it("starts unfiltered on a direct visit with no query string", async () => {
      renderAtRoute("/admin/vendors");

      await waitFor(() => {
        expect(mockFetchVendors).toHaveBeenCalledWith({ status: null, page: 0 });
      });
      expect(screen.getByLabelText(/approval status/i)).toHaveValue("");
    });

    it("ignores a status the backend does not know instead of sending it", async () => {
      renderAtRoute("/admin/vendors?status=NOT_A_STATUS");

      await waitFor(() => {
        expect(mockFetchVendors).toHaveBeenCalledWith({ status: null, page: 0 });
      });
      // `AdminVendorController` binds `status` to the enum and would answer an
      // unknown value with a 400, so a bad deep link must degrade to no filter.
      expect(screen.getByLabelText(/approval status/i)).toHaveValue("");
    });

    it("re-applies the filter when the query string changes under a mounted page", async () => {
      mockFetchVendors.mockResolvedValue(makePage<VendorProfile>([makeVendorProfile()]));

      renderWithProviders(
        <>
          <Link to="/admin/vendors?status=APPROVED">other</Link>
          <Routes>
            <Route path="/admin/vendors" element={<AdminVendorsPage />} />
          </Routes>
        </>,
        { route: "/admin/vendors?status=PENDING_APPROVAL" },
      );
      await waitFor(() => {
        expect(screen.getByLabelText(/approval status/i)).toHaveValue("PENDING_APPROVAL");
      });

      // React Router keeps the page mounted across a query-string change, so
      // initialisation alone would not pick this up.
      await userEvent.click(screen.getByRole("link", { name: "other" }));

      await waitFor(() => {
        expect(screen.getByLabelText(/approval status/i)).toHaveValue("APPROVED");
      });
      await waitFor(() => {
        expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: "APPROVED", page: 0 });
      });
    });

    it("leaves a filter chosen in the UI alone rather than re-reading the URL", async () => {
      renderAtRoute("/admin/vendors?status=PENDING_APPROVAL");
      await waitForRows();

      await userEvent.selectOptions(screen.getByLabelText(/approval status/i), "APPROVED");

      await waitFor(() => {
        expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: "APPROVED", page: 0 });
      });
      expect(screen.getByLabelText(/approval status/i)).toHaveValue("APPROVED");
    });
  });

  it("disables Previous on the first page and Next on the last", async () => {
    setup(
      makePage<VendorProfile>([makeVendorProfile()], {
        totalElements: 25,
        totalPages: 2,
        first: false,
        last: true,
        page: 1,
      }),
    );
    await waitForRows();

    expect(screen.getByText(/page 2 of 2/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /next/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /previous/i })).toBeEnabled();
  });

  it("closes the dialog without acting when cancelled", async () => {
    setup();
    await waitForRows();

    const dialog = await openDialog(/^reject$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /cancel/i }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockReject).not.toHaveBeenCalled();
  });
});