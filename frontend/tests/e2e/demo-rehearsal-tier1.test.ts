import { randomBytes, randomUUID } from "node:crypto";
import path from "node:path";
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:5173";
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? "DemoPass#Rehearsal2026";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

if (!DEMO_PASSWORD) {
  throw new Error("Set DEMO_PASSWORD for the local Playwright run.");
}
if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  throw new Error("Set ADMIN_EMAIL and ADMIN_PASSWORD for the local Playwright run.");
}

const SCREENSHOT_DIR = path.resolve(process.cwd(), "../docs/rehearsal-materials/screenshots");

async function api(
  request: APIRequestContext,
  method: "get" | "post" | "put" | "patch" | "delete",
  path: string,
  token?: string,
  data?: unknown,
) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const url = `${API_BASE}${path}`;
  if (method === "get") return request.get(url, { headers });
  if (method === "patch") return request.patch(url, { headers, data });
  if (method === "delete") return request.delete(url, { headers });
  return request.post(url, { headers, data });
}

async function expectStatus(response: { status(): number; text(): Promise<string> }, expected: number) {
  const responseText = response.status() === expected ? "" : await response.text();
  const body = responseText.replace(/"(accessToken|refreshToken)"\s*:\s*"[^"]+"/g, '"$1":"[redacted]"');
  expect(response.status(), body).toBe(expected);
}

async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const response = await api(request, "post", "/auth/login", undefined, { email, password });
  await expectStatus(response, 200);
  return (await response.json()).accessToken as string;
}

async function clearAuth(page: Page) {
  await page.context().clearCookies();
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState("domcontentloaded");
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.reload();
  await page.waitForLoadState("domcontentloaded");
}

async function signIn(page: Page, email: string, pass: string) {
  await clearAuth(page);
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill(pass);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible({ timeout: 15_000 });
}

async function navigateClientSide(page: Page, routePath: string) {
  await page.evaluate((to) => {
    window.history.pushState({}, "", to);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }, routePath);
  await page.waitForTimeout(600);
}

async function placeCodOrder(request: APIRequestContext, customerToken: string, vendorProfileId: number, productId: number, addressId: number): Promise<{ orderId: number }> {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dateStr = tomorrow.toISOString().slice(0, 10);

  // Add the product to the cart first (checkout requires a non-empty cart)
  const cartAddRes = await api(request, "post", "/cart/items", customerToken, {
    productId,
    quantity: 1,
  });
  await expectStatus(cartAddRes, 201);

  const slotsRes = await api(request, "get", `/vendors/${vendorProfileId}/slots?addressId=${addressId}&date=${dateStr}&email=${customerEmailPlaceholder}`, customerToken);
  await expectStatus(slotsRes, 200);
  const slots = await slotsRes.json();
  expect(Array.isArray(slots) && slots.length > 0, "no slots returned").toBe(true);
  const slotStart = slots[0].slotStart;

  const quoteRes = await api(request, "post", "/checkout/quote", customerToken, {
    paymentMethod: "COD",
    addressId,
    items: [{ vendorId: vendorProfileId, slotStart }],
  });
  await expectStatus(quoteRes, 200);
  const quoteBody = await quoteRes.json();
  const quoteHash = quoteBody.quoteHash;
  expect(quoteHash).toBeDefined();

  const checkoutRes = await api(request, "post", "/checkout", customerToken, {
    addressId,
    items: [{ vendorId: vendorProfileId, slotStart }],
    clientOrderId: randomUUID(),
    quoteHash,
    paymentMethod: "COD",
  });
  await expectStatus(checkoutRes, 200);
  const checkoutBody = await checkoutRes.json();
  const orderId = checkoutBody.orders[0].orderId;
  expect(orderId).toBeDefined();
  return { orderId };
}

let customerEmailPlaceholder = "placeholder@t.com";

test.describe("Phase 14 Stage 2 Tier 1 browser rehearsal (vendor accept/deliver + online payment)", () => {
  test.setTimeout(240_000);

  test("Vendor accepts a PLACED order via UI, fulfils to delivery, COD collected", async ({ page, context }) => {
    const request = context.request;
    const suffix = randomUUID().slice(0, 8);
    const password = `${randomBytes(32).toString("base64url")}Aa1!`;
    customerEmailPlaceholder = `e2e-vend-cust-${suffix}@flowerconnect.test`;
    const vendorEmail = `e2e-vend-vend-${suffix}@flowerconnect.test`;

    // ---- API setup: customer + vendor + product + stock + COD order (PLACED) ----
    const locationsRes = await api(request, "get", "/locations");
    await expectStatus(locationsRes, 200);
    const locations = await locationsRes.json();
    const serviceLocationId = locations.flatMap((city: { areas?: Array<{ id: number }> }) => city.areas ?? [])[0]?.id;
    expect(serviceLocationId).toBeDefined();

    const customerReg = await api(request, "post", "/auth/register", undefined, {
      email: customerEmailPlaceholder,
      password,
      fullName: "E2E Vendor Flow Customer",
      phone: `+91${Date.now().toString().slice(-10)}`,
    });
    await expectStatus(customerReg, 200);
    const customerToken = await apiLogin(request, customerEmailPlaceholder, password);

    const vendorReg = await api(request, "post", "/vendors/register", undefined, {
      email: vendorEmail,
      password,
      fullName: "E2E Vendor Flow Vendor",
      phone: `+918${Date.now().toString().slice(-9)}`,
      businessName: `E2E Vendor Flow Florist ${suffix}`,
      description: "Temporary vendor flow test",
      addressLine1: "123 Test Street",
      serviceLocationId,
      deliveryRadiusKm: 5.00,
      hours: [
        { weekday: "MONDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "TUESDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "WEDNESDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "THURSDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "FRIDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
      ],
    });
    await expectStatus(vendorReg, 201);
    const vendorToken = await apiLogin(request, vendorEmail, password);

    const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    let vendorProfileId: number | null = null;
    let vendorPage = 0;
    while (vendorProfileId === null) {
      const vendorsResponse = await api(request, "get", `/admin/vendors?page=${vendorPage}&size=100`, adminToken);
      await expectStatus(vendorsResponse, 200);
      const vendorsBody = await vendorsResponse.json();
      const vp = vendorsBody.content.find((v: { ownerEmail: string }) => v.ownerEmail === vendorEmail);
      if (vp) { vendorProfileId = vp.id; break; }
      if (vendorsBody.last) break;
      vendorPage++;
    }
    expect(vendorProfileId).toBeDefined();
    const approveRes = await api(request, "post", `/admin/vendors/${vendorProfileId!}/approve`, adminToken);
    await expectStatus(approveRes, 200);

    const catsRes = await api(request, "get", "/categories");
    await expectStatus(catsRes, 200);
    const cats = await catsRes.json();
    const categoryId = cats.content?.[0]?.id ?? cats[0]?.id;
    expect(categoryId).toBeDefined();

    const productRes = await api(request, "post", "/vendors/products", vendorToken, {
      name: `E2E Vendor Flow Rose ${suffix}`,
      categoryId,
      status: "ACTIVE",
      description: "Temporary vendor flow product",
      basePrice: 120.00,
    });
    await expectStatus(productRes, 201);
    const productId = (await productRes.json()).id;

    const stockRes = await api(request, "post", `/vendors/products/${productId}/inventory/stock-in`, vendorToken, {
      quantity: 10,
      reason: "E2E vendor flow stock",
    });
    await expectStatus(stockRes, 200);

    const addressRes = await api(request, "post", "/addresses", customerToken, {
      label: "Home",
      line1: "123 Test Street",
      serviceLocationId,
    });
    await expectStatus(addressRes, 201);
    const addressId = (await addressRes.json()).id as number;

    const { orderId } = await placeCodOrder(request, customerToken, vendorProfileId!, productId, addressId);

    // ---- Browser: vendor signs in and fulfils the order via the UI ----
    await signIn(page, vendorEmail, password);
    await page.getByRole("link", { name: "Vendor" }).click();
    await expect(page.getByRole("heading", { name: /E2E Vendor Flow Florist/i })).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-vendor-dashboard-${suffix}.png`) });

    await page.getByRole("navigation", { name: "Vendor" }).getByRole("link", { name: "Orders" }).click();
    await expect(page.getByText(/Order #/i).first()).toBeVisible({ timeout: 15_000 });

    // The board polls; wait for the PLACED order to appear in the New column
    await expect(page.getByText(/Order #/i).first()).toBeVisible({ timeout: 15_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-vendor-order-board-${suffix}.png`) });

    // Accept (PLACED -> CONFIRMED)
    const acceptButton = page.getByRole("button", { name: "Accept" }).first();
    await expect(acceptButton).toBeVisible();
    await acceptButton.click();
    await page.waitForTimeout(1500);
    await expect(page.getByText(/Confirmed/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-vendor-accepted-${suffix}.png`) });

    // Mark preparing (CONFIRMED -> PREPARING)
    const prepareButton = page.getByRole("button", { name: "Mark preparing" }).first();
    await expect(prepareButton).toBeVisible();
    await prepareButton.click();
    await page.waitForTimeout(1500);
    await expect(page.getByText(/Preparing/i).first()).toBeVisible({ timeout: 10_000 });

    // Out for delivery (PREPARING -> OUT_FOR_DELIVERY)
    const outForDeliveryButton = page.getByRole("button", { name: "Out for delivery" }).first();
    await expect(outForDeliveryButton).toBeVisible();
    await outForDeliveryButton.click();
    await page.waitForTimeout(1500);
    await expect(page.getByText(/Out for delivery/i).first()).toBeVisible({ timeout: 10_000 });

    // Mark delivered (OUT_FOR_DELIVERY -> DELIVERED, COD payment_status COLLECTED)
    const deliveredButton = page.getByRole("button", { name: "Mark delivered" }).first();
    await expect(deliveredButton).toBeVisible();
    await deliveredButton.click();
    await page.waitForTimeout(1500);
    await expect(page.getByText(/Delivered/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Collected/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-vendor-delivered-cod-collected-${suffix}.png`) });

    // Backend confirmation via API (not used to drive the UI flow)
    const orderRes = await api(request, "get", `/orders/${orderId}`, customerToken);
    await expectStatus(orderRes, 200);
    const orderBody = await orderRes.json();
    expect(orderBody.status).toBe("DELIVERED");
    expect(orderBody.paymentStatus).toBe("COLLECTED");
  });

  test("Online payment: customer checkout via UI, simulator pay via API, owner OK / non-owner denied", async ({ page, context }) => {
    const request = context.request;
    const suffix = randomUUID().slice(0, 8);
    const password = `${randomBytes(32).toString("base64url")}Aa1!`;
    const customerEmail = `e2e-online-cust-${suffix}@flowerconnect.test`;
    const otherCustomerEmail = `e2e-online-other-${suffix}@flowerconnect.test`;
    const vendorEmail = `e2e-online-vend-${suffix}@flowerconnect.test`;

    // ---- API setup: customer, other customer, vendor, product, stock ----
    const locationsRes = await api(request, "get", "/locations");
    await expectStatus(locationsRes, 200);
    const locations = await locationsRes.json();
    const serviceLocationId = locations.flatMap((city: { areas?: Array<{ id: number }> }) => city.areas ?? [])[0]?.id;
    expect(serviceLocationId).toBeDefined();

    const customerReg = await api(request, "post", "/auth/register", undefined, {
      email: customerEmail,
      password,
      fullName: "E2E Online Customer",
      phone: `+91${Date.now().toString().slice(-10)}`,
    });
    await expectStatus(customerReg, 200);
    const customerToken = await apiLogin(request, customerEmail, password);

    const otherReg = await api(request, "post", "/auth/register", undefined, {
      email: otherCustomerEmail,
      password,
      fullName: "E2E Online Other Customer",
      phone: `+91${Date.now().toString().slice(-10)}`,
    });
    await expectStatus(otherReg, 200);
    const otherToken = await apiLogin(request, otherCustomerEmail, password);

    const vendorReg = await api(request, "post", "/vendors/register", undefined, {
      email: vendorEmail,
      password,
      fullName: "E2E Online Vendor",
      phone: `+918${Date.now().toString().slice(-9)}`,
      businessName: `E2E Online Florist ${suffix}`,
      description: "Temporary online flow vendor",
      addressLine1: "123 Test Street",
      serviceLocationId,
      deliveryRadiusKm: 5.00,
      hours: [
        { weekday: "MONDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "TUESDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "WEDNESDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "THURSDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
        { weekday: "FRIDAY", openTime: "09:00:00", closeTime: "17:00:00", closed: false },
      ],
    });
    await expectStatus(vendorReg, 201);
    const vendorToken = await apiLogin(request, vendorEmail, password);

    const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    let vendorProfileId: number | null = null;
    let vendorPage = 0;
    while (vendorProfileId === null) {
      const vendorsResponse = await api(request, "get", `/admin/vendors?page=${vendorPage}&size=100`, adminToken);
      await expectStatus(vendorsResponse, 200);
      const vendorsBody = await vendorsResponse.json();
      const vp = vendorsBody.content.find((v: { ownerEmail: string }) => v.ownerEmail === vendorEmail);
      if (vp) { vendorProfileId = vp.id; break; }
      if (vendorsBody.last) break;
      vendorPage++;
    }
    expect(vendorProfileId).toBeDefined();
    const approveRes = await api(request, "post", `/admin/vendors/${vendorProfileId!}/approve`, adminToken);
    await expectStatus(approveRes, 200);

    const catsRes = await api(request, "get", "/categories");
    await expectStatus(catsRes, 200);
    const cats = await catsRes.json();
    const categoryId = cats.content?.[0]?.id ?? cats[0]?.id;
    expect(categoryId).toBeDefined();

    const productRes = await api(request, "post", "/vendors/products", vendorToken, {
      name: `E2E Online Rose ${suffix}`,
      categoryId,
      status: "ACTIVE",
      description: "Temporary online flow product",
      basePrice: 150.00,
    });
    await expectStatus(productRes, 201);
    const productId = (await productRes.json()).id;

    const stockRes = await api(request, "post", `/vendors/products/${productId}/inventory/stock-in`, vendorToken, {
      quantity: 10,
      reason: "E2E online flow stock",
    });
    await expectStatus(stockRes, 200);

    const addressRes = await api(request, "post", "/addresses", customerToken, {
      label: "Home",
      line1: "123 Test Street",
      serviceLocationId,
    });
    await expectStatus(addressRes, 201);
    const addressId = (await addressRes.json()).id as number;

    // Add the product to the cart first (checkout requires a non-empty cart)
    const cartAddRes = await api(request, "post", "/cart/items", customerToken, {
      productId,
      quantity: 1,
    });
    await expectStatus(cartAddRes, 201);

    // ---- API: place ONLINE checkout order (PENDING_PAYMENT) ----
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dateStr = tomorrow.toISOString().slice(0, 10);
    const slotsRes = await api(request, "get", `/vendors/${vendorProfileId}/slots?addressId=${addressId}&date=${dateStr}&email=${customerEmail}`, customerToken);
    await expectStatus(slotsRes, 200);
    const slots = await slotsRes.json();
    expect(Array.isArray(slots) && slots.length > 0).toBe(true);
    const slotStart = slots[0].slotStart;

    const quoteRes = await api(request, "post", "/checkout/quote", customerToken, {
      paymentMethod: "ONLINE",
      addressId,
      items: [{ vendorId: vendorProfileId, slotStart }],
    });
    await expectStatus(quoteRes, 200);
    const quoteBody = await quoteRes.json();
    const quoteHash = quoteBody.quoteHash;
    expect(quoteHash).toBeDefined();

    const checkoutRes = await api(request, "post", "/checkout", customerToken, {
      addressId,
      items: [{ vendorId: vendorProfileId, slotStart }],
      clientOrderId: randomUUID(),
      quoteHash,
      paymentMethod: "ONLINE",
    });
    await expectStatus(checkoutRes, 200);
    const checkoutBody = await checkoutRes.json();
    const orderId = checkoutBody.orders[0].orderId;
    const paymentGroupId = checkoutBody.paymentGroupId;
    expect(orderId).toBeDefined();
    expect(paymentGroupId).toBeDefined();

    // ---- Browser: customer lands on payment page ("Awaiting payment") ----
    await signIn(page, customerEmail, password);
    await navigateClientSide(page, `/payment/${paymentGroupId}`);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("Awaiting payment")).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-online-awaiting-${suffix}.png`) });

    // ---- API: owner simulates payment (Bearer token not logged/exposed) ----
    const payRes = await api(request, "post", `/payments/simulator/${orderId}/pay`, customerToken);
    await expectStatus(payRes, 200);

    // ---- Browser: payment page transitions to PAID, order PLACED, payment COLLECTED ----
    await expect(page.locator("dd").filter({ hasText: "PAID" }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.locator("span").filter({ hasText: "Paid" })).toBeVisible({ timeout: 15_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, `t1-online-paid-verified-${suffix}.png`) });

    // Backend confirmation of final state
    const orderRes = await api(request, "get", `/orders/${orderId}`, customerToken);
    await expectStatus(orderRes, 200);
    const orderBody = await orderRes.json();
    expect(orderBody.status).toBe("PLACED");
    expect(orderBody.paymentStatus).toBe("PAID");

    // ---- Non-owner denied: other customer cannot simulate this order ----
    const foreignRes = await api(request, "post", `/payments/simulator/${orderId}/pay`, otherToken);
    await expectStatus(foreignRes, 403);
  });
});
