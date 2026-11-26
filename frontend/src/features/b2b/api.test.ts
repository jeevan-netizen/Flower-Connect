import { describe, expect, it, vi, beforeEach } from "vitest";
import type { AxiosResponse } from "axios";

import * as apiModule from "@/features/b2b/api";
import api from "@/shared/lib/api";
import type {
  B2BOrderResponse,
  B2BOrderPageResponse,
  B2BProductSummary,
  B2BStatementPageResponse,
  B2BStatementPreviewResponse,
  B2BStatementResponse,
  SellerSummary,
} from "@/features/b2b/types";

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const mockGet = vi.mocked(api.get);
const mockPost = vi.mocked(api.post);

function response<T>(data: T): AxiosResponse<T> {
  return { data, status: 200, statusText: "OK", headers: {}, config: {} } as unknown as AxiosResponse<T>;
}

describe("B2B API client", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("fetchSellers calls GET /b2b/sellers with optional query", async () => {
    const sellers: SellerSummary[] = [
      { vendorId: 7, businessName: "Petal & Stem", addressLine1: "12 MG Road", city: "Bengaluru", area: "Indiranagar", pincode: "560038", deliveryRadiusKm: 5 },
    ];
    mockGet.mockResolvedValue(response(sellers));

    const result = await apiModule.fetchSellers("rose");

    expect(mockGet).toHaveBeenCalledWith("/vendors/b2b/sellers", { params: { query: "rose" }, signal: undefined });
    expect(result).toEqual(sellers);
  });

  it("fetchSellerProducts calls GET /b2b/sellers/{id}/products", async () => {
    const products: B2BProductSummary[] = [
      { productId: 101, productName: "Red Rose Bouquet", unitPrice: 899, unit: "bunch", availableStock: 24 },
    ];
    mockGet.mockResolvedValue(response(products));

    const result = await apiModule.fetchSellerProducts(11);

    expect(mockGet).toHaveBeenCalledWith("/vendors/b2b/sellers/11/products", { signal: undefined });
    expect(result).toEqual(products);
  });

  it("placeB2BOrder calls POST /b2b/orders with items", async () => {
    const order: B2BOrderResponse = {
      id: 1,
      orderNo: "B2B-1",
      buyerId: 7,
      buyerName: "Buyer",
      sellerId: 11,
      sellerName: "Seller",
      status: "PLACED",
      paymentStatus: "UNPAID",
      total: 1823,
      orderNote: null,
      shipmentReference: null,
      shippedAt: null,
      deliveredAt: null,
      billNumber: null,
      placedAt: "2026-11-25T10:00:00",
      updatedAt: "2026-11-25T10:00:00",
      items: [],
    };
    mockPost.mockResolvedValue(response(order));

    const result = await apiModule.placeB2BOrder({
      sellerId: 11,
      items: [{ productId: 101, quantity: 2 }],
      orderNote: "Please wrap",
    });

    expect(mockPost).toHaveBeenCalledWith("/vendors/b2b/orders", {
      sellerId: 11,
      items: [{ productId: 101, quantity: 2 }],
      orderNote: "Please wrap",
    });
    expect(result).toEqual(order);
  });

  it("fetchB2BOrders passes role, status, page and size", async () => {
    const page: B2BOrderPageResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    mockGet.mockResolvedValue(response(page));

    await apiModule.fetchB2BOrders({ role: "SELLER", status: "DELIVERED", page: 1, size: 10 });

    expect(mockGet).toHaveBeenCalledWith("/vendors/b2b/orders", {
      params: { role: "SELLER", status: "DELIVERED", page: 1, size: 10 },
      signal: undefined,
    });
  });

  it("previewB2BStatement passes buyerId and optional dates", async () => {
    const preview: B2BStatementPreviewResponse = { orders: [], totalAmount: 0 };
    mockGet.mockResolvedValue(response(preview));

    await apiModule.previewB2BStatement(7, "2026-11-10", "2026-11-24");

    expect(mockGet).toHaveBeenCalledWith("/vendors/b2b/statements/preview", {
      params: { buyerId: 7, from: "2026-11-10", to: "2026-11-24" },
      signal: undefined,
    });
  });

  it("createB2BStatement calls POST /b2b/statements", async () => {
    const statement: B2BStatementResponse = {
      id: 1,
      statementNo: "STM-1",
      sellerId: 11,
      sellerName: "Seller",
      buyerId: 7,
      buyerName: "Buyer",
      periodStart: "2026-11-10T00:00:00",
      periodEnd: "2026-11-24T23:59:59.999999",
      totalAmount: 1823,
      status: "OPEN",
      paidAt: null,
      paymentMode: null,
      paymentNote: null,
      createdAt: "2026-11-25T10:00:00",
      updatedAt: "2026-11-25T10:00:00",
      orders: [],
    };
    mockPost.mockResolvedValue(response(statement));

    const result = await apiModule.createB2BStatement({ buyerId: 7, from: "2026-11-10", to: "2026-11-24" });

    expect(mockPost).toHaveBeenCalledWith("/vendors/b2b/statements", {
      buyerId: 7,
      from: "2026-11-10",
      to: "2026-11-24",
    });
    expect(result).toEqual(statement);
  });

  it("fetchB2BStatements passes role, page and size", async () => {
    const page: B2BStatementPageResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    mockGet.mockResolvedValue(response(page));

    await apiModule.fetchB2BStatements({ role: "BUYER", page: 2, size: 5 });

    expect(mockGet).toHaveBeenCalledWith("/vendors/b2b/statements", {
      params: { role: "BUYER", page: 2, size: 5 },
      signal: undefined,
    });
  });

  it("recordB2BStatementPayment calls POST /b2b/statements/{id}/payment", async () => {
    const statement: B2BStatementResponse = {
      id: 1,
      statementNo: "STM-1",
      sellerId: 11,
      sellerName: "Seller",
      buyerId: 7,
      buyerName: "Buyer",
      periodStart: "2026-11-10T00:00:00",
      periodEnd: "2026-11-24T23:59:59.999999",
      totalAmount: 1823,
      status: "PAID",
      paidAt: "2026-11-25T11:00:00",
      paymentMode: "UPI",
      paymentNote: "Received",
      createdAt: "2026-11-25T10:00:00",
      updatedAt: "2026-11-25T11:00:00",
      orders: [],
    };
    mockPost.mockResolvedValue(response(statement));

    const result = await apiModule.recordB2BStatementPayment(1, {
      paymentMode: "UPI",
      paymentNote: "Received",
    });

    expect(mockPost).toHaveBeenCalledWith("/vendors/b2b/statements/1/payment", {
      paymentMode: "UPI",
      paymentNote: "Received",
      paidAt: null,
    });
    expect(result).toEqual(statement);
  });
});
