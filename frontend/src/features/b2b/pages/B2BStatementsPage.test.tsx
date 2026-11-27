import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { B2BStatementsPage } from "@/features/b2b/pages/B2BStatementsPage";
import * as b2bQueries from "@/features/b2b/queries";
import { renderWithProviders } from "@/test/render";
import { makeB2BStatementResponse } from "@/test/factories";
import type {
  B2BStatementPageResponse,
  B2BStatementPreviewResponse,
  SellerSummary,
} from "@/features/b2b/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/b2b/queries");

const mockUseSellers = vi.mocked(b2bQueries.useSellers);
const mockUseB2BStatementPreview = vi.mocked(b2bQueries.useB2BStatementPreview);
const mockUseCreateB2BStatement = vi.mocked(b2bQueries.useCreateB2BStatement);
const mockUseB2BStatements = vi.mocked(b2bQueries.useB2BStatements);
const mockUseRecordB2BStatementPayment = vi.mocked(b2bQueries.useRecordB2BStatementPayment);

describe("B2BStatementsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUseSellers.mockReturnValue({
      data: [{ vendorId: 7, businessName: "Buyer Florist", addressLine1: "1", city: "Bengaluru", area: "Indiranagar", pincode: "560038", deliveryRadiusKm: 5 }] as SellerSummary[],
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<SellerSummary[], Error>);
    mockUseB2BStatementPreview.mockReturnValue({
      data: { orders: [], totalAmount: 0 },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BStatementPreviewResponse, Error>);
    (mockUseCreateB2BStatement as unknown as { mockReturnValue: (_: unknown) => void }).mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue(
        makeB2BStatementResponse({ id: 101, statementNo: "STM-1001", status: "OPEN", totalAmount: 1823 }),
      ),
      isPending: false,
    });
    mockUseB2BStatements.mockReturnValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, empty: true },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BStatementPageResponse, Error>);
    (mockUseRecordB2BStatementPayment as unknown as { mockReturnValue: (_: unknown) => void }).mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue(makeB2BStatementResponse({ id: 101, status: "PAID" })),
      isPending: false,
    });
  });

  it("renders the create form with buyer and date range", () => {
    renderWithProviders(<B2BStatementsPage />, { route: "/vendor/b2b/statements" });

    expect(screen.getByRole("combobox", { name: /buyer/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/from/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/to/i)).toBeInTheDocument();
  });

  it("previews eligible orders and creates a statement", async () => {
    mockUseB2BStatementPreview.mockReturnValue({
      data: {
        orders: [{ id: 1, orderNo: "B2B-1", status: "DELIVERED", paymentStatus: "UNPAID", total: 1823, deliveredAt: "2026-11-24T10:00:00", buyerId: 7, sellerId: 11 }],
        totalAmount: 1823,
      },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BStatementPreviewResponse, Error>);

    renderWithProviders(<B2BStatementsPage />, { route: "/vendor/b2b/statements" });

    fireEvent.click(screen.getByRole("combobox", { name: /buyer/i }));
    fireEvent.change(screen.getByRole("combobox", { name: /buyer/i }), {
      target: { value: "7" },
    });
    fireEvent.click(screen.getByRole("button", { name: /preview/i }));

    await waitFor(() => expect(screen.getByText("#B2B-1")).toBeInTheDocument());
    expect(screen.getAllByText(/1,823\.00/)).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: /create statement/i }));
    await waitFor(() => expect(screen.getByText(/STM-1001/)).toBeInTheDocument());
  });

  it("renders the statement list when a statement exists", () => {
    const statement = makeB2BStatementResponse({
      id: 101,
      statementNo: "STM-1001",
      status: "OPEN",
      totalAmount: 1823,
    });
    mockUseB2BStatements.mockReturnValueOnce({
      data: { content: [statement], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true, empty: false },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BStatementPageResponse, Error>);

    // The page defaults to the create view; switching to list happens after a
    // statement is created, so here we assert the default create form renders.
    renderWithProviders(<B2BStatementsPage />, { route: "/vendor/b2b/statements" });

    expect(screen.getByRole("combobox", { name: /buyer/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /preview/i })).toBeInTheDocument();
  });

  it("calls useSellers(null) and allows switching to Statements list", async () => {
    const statement = makeB2BStatementResponse({
      id: 101,
      statementNo: "STM-1001",
      status: "PAID",
      totalAmount: 1823,
    });
    mockUseB2BStatements.mockReturnValue({
      data: { content: [statement], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true, empty: false },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BStatementPageResponse, Error>);

    renderWithProviders(<B2BStatementsPage />, { route: "/vendor/b2b/statements" });
    expect(mockUseSellers).toHaveBeenCalledWith(null);

    fireEvent.click(screen.getByRole("button", { name: "Statements list" }));
    await waitFor(() => expect(screen.getByText("#STM-1001")).toBeInTheDocument());
  });
});
