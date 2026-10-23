import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { OrderStatusStepper } from "@/features/customer/orders/components/OrderStatusStepper";
import type { OrderStatus } from "@/features/customer/orders/types";

const STATUSES: OrderStatus[] = [
  "PLACED",
  "CONFIRMED",
  "PREPARING",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "CANCELLED",
  "REJECTED",
];

describe("OrderStatusStepper", () => {
  it("renders all five lifecycle steps", () => {
    render(<OrderStatusStepper status="PLACED" />);
    expect(screen.getByText("Order placed")).toBeInTheDocument();
    expect(screen.getByText("Confirmed")).toBeInTheDocument();
    expect(screen.getByText("Preparing")).toBeInTheDocument();
    expect(screen.getByText("Out for delivery")).toBeInTheDocument();
    expect(screen.getByText("Delivered")).toBeInTheDocument();
  });

  it.each(STATUSES.map((status) => [status] as [OrderStatus]))(
    "renders without crashing for status %s",
    (status) => {
      render(<OrderStatusStepper status={status} />);
      expect(screen.getByText("Order placed")).toBeInTheDocument();
    },
  );

  it("shows a heading when provided", () => {
    render(<OrderStatusStepper status="PLACED" heading="Your order" />);
    expect(screen.getByText("Your order")).toBeInTheDocument();
  });
});
