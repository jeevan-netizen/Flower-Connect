import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  STOCK_ACTION_LABELS,
  StockActionDialog,
  explainStockFailure,
} from "@/features/vendor/components/StockActionDialog";
import { renderWithProviders } from "@/test/render";
import { makeInventory } from "@/test/factories";
import { validationError } from "@/test/api-errors";
import { toApiError } from "@/shared/lib/api-error";
import type { StockActionKind } from "@/features/vendor/types";

const onConfirm = vi.hoisted(() => vi.fn());
const onCancel = vi.hoisted(() => vi.fn());

function renderDialog(
  action: StockActionKind,
  overrides: { errorMessage?: string | null; inventory?: ReturnType<typeof makeInventory> | null } = {},
) {
  return renderWithProviders(
    <StockActionDialog
      action={action}
      productName="Red Rose Bouquet"
      inventory={overrides.inventory === undefined ? makeInventory() : overrides.inventory}
      isSubmitting={false}
      errorMessage={overrides.errorMessage ?? null}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  );
}

describe("StockActionDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onConfirm.mockResolvedValue(undefined);
  });

  it("names the product and states the level it is about to change", () => {
    renderDialog("stock-in", {
      inventory: makeInventory({ quantity: 12, available: 10, reservedQuantity: 2 }),
    });

    expect(
      screen.getByText(/Red Rose Bouquet — record units you have received/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/currently 12 on hand, 10 available, 2 reserved/i)).toBeInTheDocument();
  });

  it("refuses an adjustment with no reason and names the rule it broke", async () => {
    const user = userEvent.setup();
    renderDialog("adjustment");

    await user.type(screen.getByLabelText(/signed change/i), "-3");
    await user.click(screen.getByRole("button", { name: /save adjustment/i }));

    expect(
      await screen.findByText(/a reason is required for a stock adjustment/i),
    ).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("refuses a write-off with no reason", async () => {
    const user = userEvent.setup();
    renderDialog("write-off");

    await user.type(screen.getByLabelText(/quantity to write off/i), "2");
    await user.click(screen.getByRole("button", { name: /^write off$/i }));

    expect(await screen.findByText(/a reason is required for a write-off/i)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("refuses a whitespace-only reason, because the field is @NotBlank", async () => {
    const user = userEvent.setup();
    renderDialog("write-off");

    await user.type(screen.getByLabelText(/quantity to write off/i), "2");
    await user.type(screen.getByLabelText(/^reason$/i), "   ");
    await user.click(screen.getByRole("button", { name: /^write off$/i }));

    expect(await screen.findByText(/a reason is required for a write-off/i)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("refuses a zero adjustment, which would record a movement that changes nothing", async () => {
    const user = userEvent.setup();
    renderDialog("adjustment");

    await user.type(screen.getByLabelText(/signed change/i), "0");
    await user.type(screen.getByLabelText(/^reason$/i), "Miscount");
    await user.click(screen.getByRole("button", { name: /save adjustment/i }));

    expect(await screen.findByText(/at least 1/i)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("sends a negative adjustment as a signed quantity with its reason", async () => {
    const user = userEvent.setup();
    renderDialog("adjustment");

    await user.type(screen.getByLabelText(/signed change/i), "-3");
    await user.type(screen.getByLabelText(/^reason$/i), "Two stems arrived bruised");
    await user.click(screen.getByRole("button", { name: /save adjustment/i }));

    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledWith({ quantity: -3, reason: "Two stems arrived bruised" });
    });
  });

  it("sends a blank reason as null on stock-in, keeping 'no reason given' distinct", async () => {
    const user = userEvent.setup();
    renderDialog("stock-in");

    await user.type(screen.getByLabelText(/quantity to add/i), "12");
    await user.click(screen.getByRole("button", { name: /add stock/i }));

    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledWith({ quantity: 12, reason: null });
    });
  });

  it("refuses a non-positive stock-out", async () => {
    const user = userEvent.setup();
    renderDialog("stock-out");

    await user.type(screen.getByLabelText(/quantity to remove/i), "0");
    await user.click(screen.getByRole("button", { name: /remove stock/i }));

    expect(await screen.findByText(/greater than zero/i)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("shows a non-field refusal above the fields and keeps the dialog open", async () => {
    const user = userEvent.setup();
    renderDialog("stock-in", {
      errorMessage: "Insufficient stock: only 3 units are available.",
    });

    expect(screen.getByRole("alert")).toHaveTextContent(/only 3 units are available/i);

    await user.type(screen.getByLabelText(/quantity to add/i), "1");
    await user.click(screen.getByRole("button", { name: /add stock/i }));

    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("attaches a server field error to the offending input", async () => {
    const user = userEvent.setup();
    onConfirm.mockRejectedValue(validationError({ quantity: "must be less than or equal to 5000" }));
    renderDialog("stock-in");

    await user.type(screen.getByLabelText(/quantity to add/i), "99999");
    await user.click(screen.getByRole("button", { name: /add stock/i }));

    expect(await screen.findByText("must be less than or equal to 5000")).toBeInTheDocument();
  });

  it("can be dismissed without writing anything", async () => {
    const user = userEvent.setup();
    renderDialog("write-off");

    await user.click(screen.getByRole("button", { name: /cancel/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});

describe("explainStockFailure", () => {
  it("explains the reserved-unit floor in the vendor's terms", () => {
    const message = explainStockFailure(
      { code: "INSUFFICIENT_STOCK", message: "Stock change would breach the reserved quantity." },
      "stock-out",
    );

    expect(message).toBe(
      "Stock change would breach the reserved quantity. Units reserved for placed orders cannot be taken by a remove stock.",
    );
  });

  it("passes any other backend message through untouched", () => {
    const message = explainStockFailure({ code: "CONFLICT", message: "Product is locked." }, "write-off");

    expect(message).toBe("Product is locked.");
  });
});

describe("STOCK_ACTION_LABELS", () => {
  it("gives every action a distinct button label", () => {
    const labels = Object.values(STOCK_ACTION_LABELS);

    expect(new Set(labels).size).toBe(labels.length);
    expect(STOCK_ACTION_LABELS["write-off"]).toBe("Write off");
  });
});

describe("explainStockFailure via toApiError", () => {
  it("reads the code off a real backend envelope", () => {
    const info = toApiError(
      validationError({ quantity: "nope" }),
    );

    expect(info.code).toBe("VALIDATION_FAILED");
  });
});