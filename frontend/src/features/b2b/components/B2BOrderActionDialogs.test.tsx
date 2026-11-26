import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

import { B2BOrderActionDialogs } from "@/features/b2b/components/B2BOrderActionDialogs";

describe("B2BOrderActionDialogs", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders the reject form with a required reason", () => {
    const onConfirm = vi.fn();
    render(
      <B2BOrderActionDialogs
        orderName="Order #1"
        openAction="reject"
        isSubmitting={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    expect(screen.getByRole("textarea")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reject order/i })).toBeDisabled();
  });

  it("enables reject confirm only when a reason is entered", async () => {
    const onConfirm = vi.fn();
    render(
      <B2BOrderActionDialogs
        orderName="Order #1"
        openAction="reject"
        isSubmitting={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    await fireEvent.change(screen.getByRole("textarea"), { target: { value: "Wrong flowers" } });
    await fireEvent.click(screen.getByRole("button", { name: /reject order/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith({ reason: "Wrong flowers" }));
  });

  it("renders the ship form with a required shipment reference", () => {
    const onConfirm = vi.fn();
    render(
      <B2BOrderActionDialogs
        orderName="Order #1"
        openAction="ship"
        isSubmitting={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    expect(screen.getByRole("textbox")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ship order/i })).toBeDisabled();
  });

  it("calls onConfirm with the shipment reference", async () => {
    const onConfirm = vi.fn();
    render(
      <B2BOrderActionDialogs
        orderName="Order #1"
        openAction="ship"
        isSubmitting={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    await fireEvent.change(screen.getByRole("textbox"), { target: { value: "TRACK-1" } });
    await fireEvent.click(screen.getByRole("button", { name: /ship order/i }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith({ shipmentReference: "TRACK-1" }));
  });
});
