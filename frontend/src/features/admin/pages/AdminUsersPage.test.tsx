import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Link, Route, Routes } from "react-router-dom";
import { AdminUsersPage } from "@/features/admin/pages/AdminUsersPage";
import { renderWithProviders } from "@/test/render";
import { makeAdminUser, makePage } from "@/test/factories";
import { businessError, validationError } from "@/test/api-errors";
import type { AdminUser } from "@/features/admin/types";
import type { UserResponse } from "@/features/auth/types";

const mockFetchUsers = vi.hoisted(() => vi.fn());
const mockUpdateStatus = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminUsers: mockFetchUsers,
  updateUserStatus: mockUpdateStatus,
}));

// The admin area needs the signed-in administrator's identity to decide which row
// is "you". Only that selector is needed from the store.
vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector: (state: { user: UserResponse | null }) => unknown) =>
    selector({ user: mockCurrentUser.user }),
}));

const mockCurrentUser = vi.hoisted(() => ({
  user: null as UserResponse | null,
}));

function setup(page = makePage<AdminUser>([makeAdminUser()])) {
  mockFetchUsers.mockResolvedValue(page);
  return renderWithProviders(<AdminUsersPage />);
}

/** The table renders only once a non-empty page of users has loaded. */
async function waitForRows() {
  await screen.findByRole("table");
}

async function openStatusDialog(action: RegExp) {
  await userEvent.click(screen.getByRole("button", { name: action }));
  return screen.findByRole("dialog");
}

describe("AdminUsersPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCurrentUser.user = {
      id: 1,
      email: "admin@example.com",
      fullName: "Ada Admin",
      phone: null,
      role: "ADMIN",
      createdAt: "2026-01-01T00:00:00",
    };
    mockUpdateStatus.mockResolvedValue(makeAdminUser({ status: "SUSPENDED" }));
  });

  it("renders identity, role and status for each account", async () => {
    setup();
    await waitForRows();

    await waitFor(() => {
      expect(screen.getByText("Bea Buyer")).toBeInTheDocument();
    });
    expect(screen.getByText("buyer@example.com")).toBeInTheDocument();
    // Scoped to the row: the role filter's <option> is also labelled "Customer".
    const row = screen.getAllByRole("row")[1]!;
    expect(within(row).getByText("Customer")).toBeInTheDocument();
    expect(within(row).getByText("Active")).toBeInTheDocument();
  });

  it("shows a loading state before the listing resolves", () => {
    setup();

    expect(screen.getByText(/loading users/i)).toBeInTheDocument();
  });

  it("shows an empty state when no account matches the filters", async () => {
    setup(makePage<AdminUser>([]));

    await waitFor(() => {
      expect(screen.getByText(/no user accounts match these filters/i)).toBeInTheDocument();
    });
  });

  it("reports a failed listing and offers a retry", async () => {
    mockFetchUsers.mockRejectedValue(businessError(500, "VALIDATION_FAILED", "Database unavailable"));
    renderWithProviders(<AdminUsersPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("reports a 403 listing as a permission problem", async () => {
    mockFetchUsers.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(<AdminUsersPage />);

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  it("offers every status except the one the account already holds", async () => {
    setup(
      makePage<AdminUser>([
        makeAdminUser({ id: 42, status: "ACTIVE" }),
        makeAdminUser({ id: 43, email: "paused@example.com", status: "SUSPENDED" }),
        makeAdminUser({ id: 44, email: "off@example.com", status: "DISABLED" }),
      ]),
    );
    await waitForRows();

    const rows = screen.getAllByRole("row").slice(1);

    // No transition matrix is invented: all three statuses are mutually
    // reachable, so the only exclusion is the status already held.
    expect(within(rows[0]!).getByRole("button", { name: "Suspended" })).toBeInTheDocument();
    expect(within(rows[0]!).getByRole("button", { name: "Disabled" })).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole("button", { name: "Active" })).not.toBeInTheDocument();

    expect(within(rows[1]!).getByRole("button", { name: "Active" })).toBeInTheDocument();
    expect(within(rows[2]!).getByRole("button", { name: "Active" })).toBeInTheDocument();
  });

  it("requires a reason for every status change, including reactivation", async () => {
    setup(makePage<AdminUser>([makeAdminUser({ status: "SUSPENDED" })]));
    await waitForRows();

    const dialog = await openStatusDialog(/^active$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /set to active/i }));

    expect(await within(dialog).findByText("Reason is required")).toBeInTheDocument();
    expect(mockUpdateStatus).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Appeal upheld");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to active/i }));

    await waitFor(() =>
      expect(mockUpdateStatus).toHaveBeenCalledWith(42, { status: "ACTIVE", reason: "Appeal upheld" }),
    );
  });

  it("suspends an active account with a reason", async () => {
    setup();
    await waitForRows();

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Chargeback fraud");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() =>
      expect(mockUpdateStatus).toHaveBeenCalledWith(42, {
        status: "SUSPENDED",
        reason: "Chargeback fraud",
      }),
    );
    await waitFor(() => {
      expect(screen.getByText(/buyer@example.com is now suspended/i)).toBeInTheDocument();
    });
  });

  it("disables the status actions on the signed-in administrator's own row", async () => {
    setup(
      makePage<AdminUser>([
        makeAdminUser({ id: 1, email: "admin@example.com", fullName: "Ada Admin" }),
        makeAdminUser({ id: 42 }),
      ]),
    );
    await waitForRows();

    const rows = screen.getAllByRole("row").slice(1);

    expect(within(rows[0]!).getByText("(you)")).toBeInTheDocument();
    expect(
      within(rows[0]!).getByText(/an administrator cannot change their own account status/i),
    ).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole("button")).not.toBeInTheDocument();

    // The other row is unaffected.
    expect(within(rows[1]!).getByRole("button", { name: "Suspended" })).toBeInTheDocument();
  });

  it("still reports a 403 if a self-targeting change is attempted anyway", async () => {
    setup(makePage<AdminUser>([makeAdminUser({ id: 42 })]));
    await waitForRows();

    mockUpdateStatus.mockRejectedValue(
      businessError(403, "FORBIDDEN", "An administrator cannot change their own account status"),
    );

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Testing the guard");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() => {
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "An administrator cannot change their own account status",
      );
    });
  });

  it("keeps the dialog open and shows the backend message on a 404", async () => {
    setup();
    await waitForRows();
    mockUpdateStatus.mockRejectedValue(businessError(404, "NOT_FOUND", "User not found"));

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Testing not found");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() => {
      expect(within(dialog).getByText("User not found")).toBeInTheDocument();
    });
  });

  it("shows a backend field-level validation message on the reason input", async () => {
    setup();
    await waitForRows();
    mockUpdateStatus.mockRejectedValue(validationError({ reason: "Reason is required" }));

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "placeholder");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() => {
      expect(within(dialog).getByText("Reason is required")).toBeInTheDocument();
    });
  });

  it("does not send a second status change while one is in flight", async () => {
    setup();
    await waitForRows();

    let resolveUpdate: (value: AdminUser) => void = () => {};
    mockUpdateStatus.mockImplementation(
      () =>
        new Promise<AdminUser>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Fraud review");
    await userEvent.click(within(dialog).getByRole("button", { name: /set to suspended/i }));

    await waitFor(() => {
      expect(within(screen.getByRole("dialog")).getByRole("button", { name: "Working..." })).toBeDisabled();
    });
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Working..." }));
    expect(mockUpdateStatus).toHaveBeenCalledTimes(1);

    resolveUpdate(makeAdminUser({ status: "SUSPENDED" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("filters by role", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^role$/i), "FLORIST");

    await waitFor(() => {
      expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: "FLORIST", status: null, page: 0 });
    });
  });

  it("filters by status and resets to the first page", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^status$/i), "DISABLED");

    await waitFor(() => {
      expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: null, status: "DISABLED", page: 0 });
    });
  });

  it("combines role and status filters", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^role$/i), "FLORIST");
    await userEvent.selectOptions(screen.getByLabelText(/^status$/i), "SUSPENDED");

    await waitFor(() => {
      expect(mockFetchUsers).toHaveBeenLastCalledWith({
        role: "FLORIST",
        status: "SUSPENDED",
        page: 0,
      });
    });
  });

  it("clears both filters back to no filter at all", async () => {
    setup();
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^role$/i), "FLORIST");
    await waitFor(() => expect(mockFetchUsers).toHaveBeenCalledTimes(2));
    await userEvent.selectOptions(screen.getByLabelText(/^role$/i), "");

    await waitFor(() => {
      expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: null, status: null, page: 0 });
    });
  });

  it("requests the next page with the zero-based index the backend expects", async () => {
    setup(
      makePage<AdminUser>([makeAdminUser()], {
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
      expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: null, status: null, page: 1 });
    });
  });

  it("keeps the active filters when paging", async () => {
    setup(
      makePage<AdminUser>([makeAdminUser()], {
        totalElements: 25,
        totalPages: 2,
        first: true,
        last: false,
      }),
    );
    await waitForRows();

    await userEvent.selectOptions(screen.getByLabelText(/^role$/i), "CUSTOMER");
    await waitFor(() => expect(mockFetchUsers).toHaveBeenCalledTimes(2));
    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: "CUSTOMER", status: null, page: 1 });
    });
  });

  /**
   * The admin dashboard deep-links into this page as
   * `/admin/users?status=SUSPENDED` ("View all N suspended accounts"). Without
   * reading the query string that link would silently open the unfiltered list.
   */
  describe("status filter seeded from the query string", () => {
    function renderAtRoute(route: string) {
      mockFetchUsers.mockResolvedValue(
        makePage<AdminUser>([makeAdminUser({ status: "SUSPENDED" })]),
      );
      return renderWithProviders(<AdminUsersPage />, { route });
    }

    it("starts filtered when the dashboard link is followed", async () => {
      renderAtRoute("/admin/users?status=SUSPENDED");

      await waitFor(() => {
        expect(mockFetchUsers).toHaveBeenCalledWith({ role: null, status: "SUSPENDED", page: 0 });
      });
      // Asserted on the control itself, so a page that queried correctly but
      // rendered the wrong selection would still fail.
      await waitFor(() => {
        expect(screen.getByLabelText(/^status$/i)).toHaveValue("SUSPENDED");
      });
    });

    it("starts unfiltered on a direct visit with no query string", async () => {
      renderAtRoute("/admin/users");

      await waitFor(() => {
        expect(mockFetchUsers).toHaveBeenCalledWith({ role: null, status: null, page: 0 });
      });
      expect(screen.getByLabelText(/^status$/i)).toHaveValue("");
    });

    it("ignores a status the backend does not know instead of sending it", async () => {
      renderAtRoute("/admin/users?status=NOT_A_STATUS");

      await waitFor(() => {
        expect(mockFetchUsers).toHaveBeenCalledWith({ role: null, status: null, page: 0 });
      });
      // `AdminUserController` binds `status` to `User.Status` and would answer an
      // unknown value with a 400, so a bad deep link must degrade to no filter.
      expect(screen.getByLabelText(/^status$/i)).toHaveValue("");
    });

    it("re-applies the filter when the query string changes under a mounted page", async () => {
      mockFetchUsers.mockResolvedValue(makePage<AdminUser>([makeAdminUser()]));

      renderWithProviders(
        <>
          <Link to="/admin/users?status=DISABLED">other</Link>
          <Routes>
            <Route path="/admin/users" element={<AdminUsersPage />} />
          </Routes>
        </>,
        { route: "/admin/users?status=SUSPENDED" },
      );
      await waitFor(() => {
        expect(screen.getByLabelText(/^status$/i)).toHaveValue("SUSPENDED");
      });

      // React Router keeps the page mounted across a query-string change, so
      // initialisation alone would not pick this up.
      await userEvent.click(screen.getByRole("link", { name: "other" }));

      await waitFor(() => {
        expect(screen.getByLabelText(/^status$/i)).toHaveValue("DISABLED");
      });
      await waitFor(() => {
        expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: null, status: "DISABLED", page: 0 });
      });
    });

    it("leaves a filter chosen in the UI alone rather than re-reading the URL", async () => {
      renderAtRoute("/admin/users?status=SUSPENDED");
      await waitForRows();

      await userEvent.selectOptions(screen.getByLabelText(/^status$/i), "DISABLED");

      await waitFor(() => {
        expect(mockFetchUsers).toHaveBeenLastCalledWith({ role: null, status: "DISABLED", page: 0 });
      });
      expect(screen.getByLabelText(/^status$/i)).toHaveValue("DISABLED");
    });
  });

  it("closes the dialog without acting when cancelled", async () => {
    setup();
    await waitForRows();

    const dialog = await openStatusDialog(/^suspended$/i);
    await userEvent.click(within(dialog).getByRole("button", { name: /cancel/i }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockUpdateStatus).not.toHaveBeenCalled();
  });
});