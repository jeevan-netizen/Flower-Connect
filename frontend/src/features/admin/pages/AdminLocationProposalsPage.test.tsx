import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminLocationProposalsPage } from "@/features/admin/pages/AdminLocationProposalsPage";
import { renderWithProviders } from "@/test/render";
import { businessError } from "@/test/api-errors";
import { makeServiceLocationProposal } from "@/test/factories";
import type { ServiceLocationProposal } from "@/features/admin/types";

const mockFetchProposals = vi.hoisted(() => vi.fn());
const mockApproveProposal = vi.hoisted(() => vi.fn());
const mockRejectProposal = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchServiceLocationProposals: mockFetchProposals,
  approveServiceLocationProposal: mockApproveProposal,
  rejectServiceLocationProposal: mockRejectProposal,
}));

function setup(proposals: ServiceLocationProposal[] = []) {
  mockFetchProposals.mockResolvedValue(proposals);
  return renderWithProviders(<AdminLocationProposalsPage />);
}

async function waitForTable() {
  await screen.findByRole("table");
}

function proposalRow() {
  return screen.getAllByRole("row").slice(1)[0]!;
}

describe("AdminLocationProposalsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApproveProposal.mockResolvedValue(undefined);
    mockRejectProposal.mockResolvedValue(undefined);
  });

  it("shows a loading state before the listing resolves", () => {
    setup();

    expect(screen.getByText(/loading proposals/i)).toBeInTheDocument();
  });

  it("shows an empty state when there are no pending proposals", async () => {
    setup([]);

    await waitFor(() => {
      expect(screen.getByText(/no pending proposals/i)).toBeInTheDocument();
    });
  });

  it("lists each pending proposal with area, proposing vendor and submitted date", async () => {
    const proposal = makeServiceLocationProposal({
      id: 11,
      area: "Bommanahalli",
      city: "Bengaluru",
      pincode: "560068",
      proposedByEmail: "vendor@example.com",
      createdAt: "2026-11-24T10:00:00",
    });
    setup([proposal]);
    await waitForTable();

    const row = proposalRow();
    expect(within(row).getByText("Bommanahalli, Bengaluru")).toBeInTheDocument();
    expect(within(row).getByText("560068")).toBeInTheDocument();
    expect(within(row).getByText("vendor@example.com")).toBeInTheDocument();
    expect(within(row).getByText("2026-11-24T10:00:00")).toBeInTheDocument();
  });

  it("reports a failed listing and offers a retry", async () => {
    mockFetchProposals.mockRejectedValue(
      businessError(500, "VALIDATION_FAILED", "Database unavailable"),
    );
    renderWithProviders(<AdminLocationProposalsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });

  it("retries the listing when Try again is clicked and renders the recovered list", async () => {
    const proposal = makeServiceLocationProposal({ id: 21 });
    mockFetchProposals
      .mockRejectedValueOnce(businessError(500, "VALIDATION_FAILED", "Database unavailable"))
      .mockResolvedValueOnce([proposal]);
    renderWithProviders(<AdminLocationProposalsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Database unavailable");
    });
    expect(mockFetchProposals).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => {
      expect(mockFetchProposals).toHaveBeenCalledTimes(2);
    });
    await waitForTable();
    expect(screen.getByRole("table")).toBeInTheDocument();
  });

  it("approves a proposal with latitude, longitude and timezone, then refreshes the list", async () => {
    const proposal = makeServiceLocationProposal({
      id: 31,
      area: "Bommanahalli",
      city: "Bengaluru",
    });
    setup([proposal]);
    await waitForTable();

    // The refetch triggered by a successful approval must return nothing so the
    // approved proposal drops out of the pending list.
    mockFetchProposals.mockResolvedValueOnce([]);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    const dialog = await screen.findByRole("dialog");
    // Latitude and longitude are `type="number"` (spinbutton) in DOM order;
    // timezone is a plain text input. Clear each before typing because the
    // timezone field is pre-filled and `type` appends.
    const [latitudeInput, longitudeInput] = within(dialog).getAllByRole("spinbutton");
    await userEvent.clear(latitudeInput);
    await userEvent.type(latitudeInput, "12.9352");
    await userEvent.clear(longitudeInput);
    await userEvent.type(longitudeInput, "77.6245");
    await userEvent.clear(within(dialog).getByRole("textbox"));
    await userEvent.type(within(dialog).getByRole("textbox"), "Asia/Kolkata");

    await userEvent.click(within(dialog).getByRole("button", { name: "Approve" }));

    await waitFor(() => {
      expect(mockApproveProposal).toHaveBeenCalledTimes(1);
    });
    const [proposalId, payload] = mockApproveProposal.mock.calls[0]!;
    expect(proposalId).toBe(31);
    expect(payload).toEqual({
      latitude: 12.9352,
      longitude: 77.6245,
      timezone: "Asia/Kolkata",
    });
    await waitFor(() => {
      expect(screen.getByText(/approved bommanahalli, bengaluru/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText(/no pending proposals/i)).toBeInTheDocument();
    });
  });

  it("rejects a proposal with a reason, then refreshes the list", async () => {
    const proposal = makeServiceLocationProposal({
      id: 41,
      area: "TestRejectArea",
      city: "Bengaluru",
    });
    setup([proposal]);
    await waitForTable();

    // The refetch triggered by a successful rejection must return nothing.
    mockFetchProposals.mockResolvedValueOnce([]);

    await userEvent.click(screen.getByRole("button", { name: "Reject" }));

    const dialog = await screen.findByRole("dialog");
    await userEvent.type(
      within(dialog).getByLabelText(/reason/i),
      "Duplicate area already covered.",
    );
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    await waitFor(() => {
      expect(mockRejectProposal).toHaveBeenCalledWith(41, {
        reason: "Duplicate area already covered.",
      });
    });
    await waitFor(() => {
      expect(screen.getByText(/rejected testrejectarea, bengaluru/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText(/no pending proposals/i)).toBeInTheDocument();
    });
  });

  it("keeps the reject dialog open and shows the backend message on failure", async () => {
    const proposal = makeServiceLocationProposal({ id: 51 });
    setup([proposal]);
    await waitForTable();
    mockRejectProposal.mockRejectedValue(
      businessError(409, "CONFLICT", "Already resolved"),
    );

    await userEvent.click(screen.getByRole("button", { name: "Reject" }));

    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/reason/i), "Some reason");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject" }));

    await waitFor(() => {
      expect(within(dialog).getByText("Already resolved")).toBeInTheDocument();
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(mockRejectProposal).toHaveBeenCalledTimes(1);
  });

  it("shows a field error and does not call the API when the approve form is invalid", async () => {
    const proposal = makeServiceLocationProposal({ id: 61 });
    setup([proposal]);
    await waitForTable();

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    const dialog = await screen.findByRole("dialog");
    // The timezone field is pre-filled, so clear it to make the mandatory field blank.
    await userEvent.clear(within(dialog).getByRole("textbox"));
    await userEvent.click(within(dialog).getByRole("button", { name: "Approve" }));

    await waitFor(() => {
      expect(within(dialog).getByText(/timezone is required/i)).toBeInTheDocument();
    });
    expect(mockApproveProposal).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
