import { test, expect } from "@playwright/test";

const ADMIN_EMAIL = "admin@flowerconnect.test";
const ADMIN_PASSWORD = "TestPassword123!";
const API_BASE = "http://localhost:8080/api/v1";

async function apiRequest(
  context: any,
  method: string,
  path: string,
  body?: any,
  token?: string,
) {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return context.request[method](`${API_BASE}${path}`, { headers, data: body });
}

async function login(context: any, email: string, password: string): Promise<string> {
  const res = await apiRequest(context, "post", "/auth/login", { email, password });
  expect(res.status()).toBe(200);
  const json = await res.json();
  return json.accessToken;
}

test.describe("Phase 8 dispute flow", () => {
  test("full lifecycle via API + UI verification", async ({ page, context }) => {
    const adminToken = await login(context, ADMIN_EMAIL, ADMIN_PASSWORD);

    // --- Setup: customer ---
    const custEmail = `e2e-cust-${Date.now()}@flowerconnect.test`;
    await apiRequest(context, "post", "/auth/register", {
      email: custEmail,
      password: "TestPassword123!",
      fullName: "E2E Customer",
      phone: `+9199999${Date.now() % 10000}`,
    });
    const custToken = await login(context, custEmail, "TestPassword123!");

    // --- Setup: vendor + approval ---
    const vendEmail = `e2e-vend-${Date.now()}@flowerconnect.test`;
    const locationsRes = await apiRequest(context, "get", "/locations");
    const locations = await locationsRes.json();
    // Hierarchical: cities contain areas; get first area's id
    const slId = locations[0]?.areas?.[0]?.id;

    const regRes = await apiRequest(context, "post", "/vendors/register", {
      email: vendEmail,
      password: "TestPassword123!",
      fullName: "E2E Vendor",
      phone: `+9188888${Date.now() % 10000}`,
      businessName: "E2E Florist",
      description: "Test florist",
      addressLine1: "123 Test St",
      serviceLocationId: slId,
    });
    if (regRes.status() !== 201) {
      console.log("Vendor register body:", await regRes.text());
    }
    expect(regRes.status()).toBe(201);
    const vendToken = await login(context, vendEmail, "TestPassword123!");

    const profileRes = await apiRequest(context, "get", "/vendors/profile", undefined, vendToken);
    const vendorProfile = await profileRes.json();
    const vendorProfileId = vendorProfile.id;

    // Admin approves vendor
    const approveRes = await apiRequest(
      context,
      "post",
      `/admin/vendors/${vendorProfileId}/approve`,
      undefined,
      adminToken,
    );
    expect(approveRes.status()).toBe(200);

    // --- Setup: product ---
    const catsRes = await apiRequest(context, "get", "/categories");
    const cats = await catsRes.json();
    const catId = cats.content[0].id;

    const prodRes = await apiRequest(
      context,
      "post",
      "/vendors/products",
      { name: "E2E Roses", categoryId: catId, description: "Test", basePrice: "100.00" },
      vendToken,
    );
    expect(prodRes.status()).toBe(201);
    const productId = (await prodRes.json()).id;

    await apiRequest(
      context,
      "post",
      `/vendors/products/${productId}/inventory/stock-in`,
      { quantity: 10, reason: "E2E stock" },
      vendToken,
    );

    // --- Setup: place order ---
    const slotsRes = await apiRequest(
      context,
      "get",
      `/vendors/${vendorProfileId}/slots?date=2026-12-01`,
      undefined,
      custToken,
    );
    expect(slotsRes.status()).toBe(200);

    const checkoutRes = await apiRequest(
      context,
      "post",
      "/checkout",
      {
        items: [{ productId, quantity: 1, note: "E2E" }],
        deliveryAddressLine1: "123 Test St",
        deliveryLatitude: 12.97,
        deliveryLongitude: 77.59,
        deliverySlot: "2026-12-01T10:00:00",
        paymentMethod: "COD",
      },
      custToken,
    );
    expect(checkoutRes.status()).toBe(200);
    const checkout = await checkoutRes.json();
    const orderId = checkout.orders[0].id;

    // Vendor accepts and delivers
    await apiRequest(context, "post", `/vendor/orders/${orderId}/accept`, undefined, vendToken);
    await apiRequest(context, "post", `/vendor/orders/${orderId}/deliver`, undefined, vendToken);

    // Verify delivered
    const orderRes = await apiRequest(context, "get", `/orders/${orderId}`, undefined, custToken);
    expect(orderRes.status()).toBe(200);
    const order = await orderRes.json();
    expect(order.status).toBe("DELIVERED");

    // ------------------------------------------------------------------
    // Customer raises dispute (UI)
    // ------------------------------------------------------------------
    await page.goto(`/orders/${orderId}`);
    await expect(page.getByRole("button", { name: /report a problem/i })).toBeVisible();
    await page.getByRole("button", { name: /report a problem/i }).click();

    await page.selectOption(page.getByLabelText(/reason/i), "ITEM_NOT_AS_DESCRIBED");
    await page.getByLabelText(/description/i).fill("Flowers arrived damaged");
    await page.getByRole("button", { name: /submit dispute/i }).click();

    await expect(page.getByText(/dispute submitted/i)).toBeVisible();

    // Get dispute ID
    const cdRes = await apiRequest(context, "get", "/disputes", undefined, custToken);
    const cd = await cdRes.json();
    const dispute = cd.content.find((d: any) => d.orderId === orderId);
    expect(dispute).toBeDefined();
    const disputeId = dispute.id;

    // ------------------------------------------------------------------
    // Vendor responds (UI, separate context)
    // ------------------------------------------------------------------
    const vendContext = await page.context().browser().newContext();
    const vendPage = await vendContext.newPage();
    await vendPage.goto("/vendor/disputes");
    await vendPage.getByRole("button", { name: /respond/i }).first().click();
    await vendPage.getByLabelText(/response/i).fill("We will replace the order");
    await vendPage.getByRole("button", { name: /submit response/i }).click();

    const vdRes = await apiRequest(
      context,
      "get",
      `/vendor/disputes/${disputeId}`,
      undefined,
      vendToken,
    );
    expect(vdRes.status()).toBe(200);
    const vd = await vdRes.json();
    expect(vd.status).toBe("UNDER_REVIEW");
    await vendContext.close();

    // ------------------------------------------------------------------
    // Admin resolves as partial refund (UI, separate context)
    // ------------------------------------------------------------------
    const admContext = await page.context().browser().newContext();
    const admPage = await admContext.newPage();
    await admPage.goto("/admin/disputes");
    await admPage.getByRole("button", { name: /resolve/i }).first().click();
    await admPage.selectOption(admPage.getByLabelText(/outcome/i), "RESOLVED_PARTIAL");
    await admPage.getByLabelText(/refund amount/i).fill("30.00");
    await admPage.getByLabelText(/resolution note/i).fill("Partial refund granted");
    await admPage.getByRole("button", { name: /resolve dispute/i }).click();

    const adRes = await apiRequest(
      context,
      "get",
      `/admin/disputes/${disputeId}`,
      undefined,
      adminToken,
    );
    expect(adRes.status()).toBe(200);
    const ad = await adRes.json();
    expect(ad.status).toBe("RESOLVED_PARTIAL");
    expect(ad.refundAmount).toBe(30.00);
    await admContext.close();

    // ------------------------------------------------------------------
    // Customer sees outcome (UI)
    // ------------------------------------------------------------------
    await page.goto(`/disputes/${disputeId}`);
    await expect(page.getByText(/RESOLVED PARTIAL/i)).toBeVisible();
    await expect(page.getByText(/30\.00/i)).toBeVisible();

    // ------------------------------------------------------------------
    // Second dispute blocked
    // ------------------------------------------------------------------
    const dupRes = await apiRequest(
      context,
      "post",
      `/disputes/orders/${orderId}`,
      { reasonCode: "ITEM_DAMAGED", description: "Second attempt" },
      custToken,
    );
    expect(dupRes.status()).toBe(409);
  });
});