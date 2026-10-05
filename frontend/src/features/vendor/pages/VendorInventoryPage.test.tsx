import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorInventoryPage } from "@/features/vendor/pages/VendorInventoryPage";
import { renderWithProviders } from "@/test/render";
import { makeLowStockProduct, makePage, makeStockMovement } from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { StockMovement } from "@/features/vendor/types";

const mockFetchLowStock = vi.hoisted(() => vi.fn());
const mockFetchMovements = vi.hoisted(() => vi.fn());
const mockStockIn = vi.hoisted(() => vi.fn());
const mockStockOut = vi.hoisted(() => vi.fn());
const mockAdjust = vi.hoisted(() => vi.fn());
const mockWriteOff = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchLowStockProducts: mockFetchLowStock,
  fetchStockMovements: mockFetchMovements,
  stockIn: mockStockIn,
  stockOut: mockStockOut,
  adjustStock: mockAdjust,
  writeOffStock: mockWriteOff,
}));

const ROSES = makeLowStockProduct();

function movementPage(movements: StockMovement[]) {
  return makePage(movements, { size: 20 });
}

function renderInventory() {
  return renderWithProviders(<VendorInventoryPage />);
}

/** Every row button lives in the stock-actions cell; find it by row to avoid ambiguity. */
function actionButton(name: string | RegExp, productName: string) {
  const row = screen.getByText(productName).closest("tr");
  if (!row) {
    throw new Error(`No row for ${productName}`);
  }
  return within(row).getByRole("button", { name });
}

describe("VendorInventoryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchLowStock.mockResolvedValue(makePage([ROSES]));
    mockFetchMovements.mockResolvedValue(movementPage([makeStockMovement()]));
    mockStockIn.mockResolvedValue(undefined);
    mockStockOut.mockResolvedValue(undefined);
    mockAdjust.mockResolvedValue(undefined);
    mockWriteOff.mockResolvedValue(undefined);
  });

  it("lists exactly the products the backend reported as low stock", async () => {
    renderInventory();

    await waitFor(() => {
      expect(mockFetchLowStock).toHaveBeenCalledWith(0, 20, expect.anything());
    });

    const row = (await screen.findByText("Red Rose Bouquet")).closest("tr")!;
    expect(within(row).getByText("3")).toBeInTheDocument();
    expect(within(row).getByText("4")).toBeInTheDocument();
  });

  it("names the reserved count only when there is one", async () => {
    mockFetchLowStock.mockResolvedValue(
      makePage([makeLowStockProduct({ quantity: 5, reservedQuantity: 2, available: 3 })]),
    );

    renderInventory();

    expect(await screen.findByText("(2 reserved)")).toBeInTheDocument();
  });

  it("says nothing is running low when the list is empty", async () => {
    mockFetchLowStock.mockResolvedValue(makePage([]));

    renderInventory();

    expect(await screen.findByText(/nothing is running low/i)).toBeInTheDocument();
  });

  it("opens the matching dialog for each of the four actions", async () => {
    const user = userEvent.setup();
    renderInventory();

    await screen.findByText("Red Rose Bouquet");

    await user.click(actionButton(/add stock stock for/i, "Red Rose Bouquet"));
    expect(await screen.findByRole("dialog", { name: /add stock/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await user.click(actionButton(/adjust stock for/i, "Red Rose Bouquet"));
    expect(await screen.findByRole("dialog", { name: /adjust stock/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await user.click(actionButton(/write off stock for/i, "Red Rose Bouquet"));
    expect(await screen.findByRole("dialog", { name: /write off stock/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await user.click(actionButton(/remove stock stock for/i, "Red Rose Bouquet"));
    expect(await screen.findByRole("dialog", { name: /remove stock/i })).toBeInTheDocument();
  });

  it("requires a reason before a write-off can be submitted", async () => {
    const user = userEvent.setup();
    renderInventory();

    await screen.findByText("Red Rose Bouquet");
    await user.click(actionButton(/write off stock for/i, "Red Rose Bouquet"));

    await user.type(screen.getByLabelText(/quantity to write off/i), "2");
    await user.click(screen.getByRole("button", { name: /^write off$/i }));

    expect(await screen.findByText(/a reason is required for a write-off/i)).toBeInTheDocument();
    expect(mockWriteOff).not.toHaveBeenCalled();
  });

  it("sends a write-off with its reason and reports the change", async () => {
    const user = userEvent.setup();
    renderInventory();

    await screen.findByText("Red Rose Bouquet");
    await user.click(actionButton(/write off stock for/i, "Red Rose Bouquet"));

    await user.type(screen.getByLabelText(/quantity to write off/i), "2");
    await user.type(screen.getByLabelText(/^reason$/i), "Spoiled overnight");
    await user.click(screen.getByRole("button", { name: /^write off$/i }));

    await waitFor(() => {
      expect(mockWriteOff).toHaveBeenCalledWith(101, { quantity: 2, reason: "Spoiled overnight" });
    });
    expect(await screen.findByText(/recorded in the movement history/i)).toBeInTheDocument();
  });

  it("explains a reserved-unit refusal and keeps the dialog open", async () => {
    const user = userEvent.setup();
    mockStockOut.mockRejectedValue(
      apiError(409, {
        code: "INSUFFICIENT_STOCK",
        message: "Stock change would breach the reserved quantity.",
      }),
    );
    renderInventory();

    await screen.findByText("Red Rose Bouquet");
    await user.click(actionButton(/remove stock stock for/i, "Red Rose Bouquet"));

    await user.type(screen.getByLabelText(/quantity to remove/i), "5");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /remove stock/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/reserved for placed orders/i);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText(/quantity to remove/i)).toHaveValue(5);
  });

  it("reads the movement history for the product a row acted on", async () => {
    const user = userEvent.setup();
    renderInventory();

    await screen.findByText("Red Rose Bouquet");
    expect(screen.getByText(/choose a stock action above/i)).toBeInTheDocument();

    await user.click(actionButton(/add stock stock for/i, "Red Rose Bouquet"));
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await waitFor(() => {
      expect(mockFetchMovements).toHaveBeenCalledWith(101, 0, 20, expect.anything());
    });
    expect(await screen.findByText(/movement history/i)).toBeInTheDocument();
    expect(await screen.findByText("Stock in")).toBeInTheDocument();
    expect(screen.getByText("Weekly delivery")).toBeInTheDocument();
  });

  it("links a low-stock row to the product editor", async () => {
    renderInventory();

    expect(await screen.findByRole("link", { name: "Red Rose Bouquet" })).toHaveAttribute(
      "href",
      "/vendor/catalog/101",
    );
  });

  it("surfaces a failed listing read with a retry", async () => {
    mockFetchLowStock.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderInventory();

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    mockFetchLowStock.mockResolvedValue(makePage([ROSES]));
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByText("Red Rose Bouquet")).toBeInTheDocument();
  });

  it("pages the low-stock list", async () => {
    const user = userEvent.setup();
    mockFetchLowStock.mockResolvedValue(
      makePage([ROSES], { totalElements: 45, totalPages: 3, last: false }),
    );
    renderInventory();

    await screen.findByText("Red Rose Bouquet");
    await user.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchLowStock).toHaveBeenLastCalledWith(1, 20, expect.anything());
    });
  });
});