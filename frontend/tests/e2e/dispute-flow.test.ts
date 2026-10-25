import { randomBytes, randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  throw new Error("Set ADMIN_EMAIL and ADMIN_PASSWORD for the local Playwright run.");
}

async function api(
  request: APIRequestContext,
  method: "get" | "post" | "put",
  path: string,
  token?: string,
  data?: unknown,
) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const url = `${API_BASE}${path}`;
  if (method === "get") return request.get(url, { headers });
  return request[method](url, { headers, data });
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

async function signIn(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/$/);
}

test("customer, vendor, and admin complete the dispute lifecycle", async ({ page, context }) => {
  test.setTimeout(180_000);

  const request = context.request;
  const browser = context.browser();
  if (!browser) throw new Error("Playwright browser context is unavailable.");
  const accountPassword = `${randomBytes(32).toString("base64url")}Aa1!`;
  const suffix = randomUUID();
  const customerEmail = `e2e-customer-${suffix}@flowerconnect.test`;
  const vendorEmail = `e2e-vendor-${suffix}@flowerconnect.test`;

  const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);

  const locationsResponse = await api(request, "get", "/locations");
  await expectStatus(locationsResponse, 200);
  const locations = await locationsResponse.json();
  const serviceLocationId = locations.flatMap((city: { areas?: Array<{ id: number }> }) => city.areas ?? [])[0]?.id;
  expect(serviceLocationId).toBeDefined();

  const customerRegistration = await api(request, "post", "/auth/register", undefined, {
    email: customerEmail,
    password: accountPassword,
    fullName: "Phase 8 E2E Customer",
    phone: `+91${Date.now().toString().slice(-10)}`,
  });
  await expectStatus(customerRegistration, 200);
  const customerToken = await apiLogin(request, customerEmail, accountPassword);

  const vendorRegistration = await api(request, "post", "/vendors/register", undefined, {
    email: vendorEmail,
    password: accountPassword,
    fullName: "Phase 8 E2E Vendor",
    phone: `+918${Date.now().toString().slice(-9)}`,
    businessName: `Phase 8 E2E Florist ${suffix.slice(0, 8)}`,
    description: "Temporary local end-to-end test vendor",
    addressLine1: "123 Test Street",
    serviceLocationId,
  });
  await expectStatus(vendorRegistration, 201);
  const vendorProfileId = (await vendorRegistration.json()).id as number;
  const vendorToken = await apiLogin(request, vendorEmail, accountPassword);

  const approval = await api(request, "post", `/admin/vendors/${vendorProfileId}/approve`, adminToken);
  await expectStatus(approval, 200);

  const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
  const profileUpdate = await api(request, "put", "/vendors/profile", vendorToken, {
    businessName: `Phase 8 E2E Florist ${suffix.slice(0, 8)}`,
    description: "Temporary local end-to-end test vendor",
    addressLine1: "123 Test Street",
    serviceLocationId,
    deliveryRadiusKm: "10.00",
    minOrderAmount: "0.00",
    baseDeliveryFee: "0.00",
    perKmFee: "0.00",
    prepTimeMinutes: 30,
    slotDurationMinutes: 60,
    maxOrdersPerSlot: 10,
    acceptingOrders: true,
    hours: weekdays.map((weekday) => ({ weekday, openTime: "09:00", closeTime: "17:00", closed: false })),
  });
  await expectStatus(profileUpdate, 200);

  const addressResponse = await api(request, "post", "/addresses", customerToken, {
    label: "Home",
    line1: "123 Test Street",
    serviceLocationId,
  });
  await expectStatus(addressResponse, 201);
  const addressId = (await addressResponse.json()).id as number;

  const categoriesResponse = await api(request, "get", "/categories?page=0&size=20");
  await expectStatus(categoriesResponse, 200);
  const categoryId = (await categoriesResponse.json()).content?.[0]?.id as number | undefined;
  expect(categoryId).toBeDefined();

  const productResponse = await api(request, "post", "/vendors/products", vendorToken, {
    name: `Phase 8 E2E Roses ${suffix}`,
    categoryId,
    description: "Temporary local end-to-end test product",
    basePrice: "100.00",
    status: "ACTIVE",
  });
  await expectStatus(productResponse, 201);
  const productId = (await productResponse.json()).id as number;

  const stockResponse = await api(
    request,
    "post",
    `/vendors/products/${productId}/inventory/stock-in`,
    vendorToken,
    { quantity: 10, reason: "Temporary Phase 8 E2E inventory" },
  );
  await expectStatus(stockResponse, 200);
  console.log("[phase-8-e2e] provisioned temporary customer/vendor, product, and inventory");

  const cartResponse = await api(request, "post", "/cart/items", customerToken, { productId, quantity: 1 });
  await expectStatus(cartResponse, 201);

  const deliveryDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const slotsResponse = await api(
    request,
    "get",
    `/vendors/${vendorProfileId}/slots?addressId=${addressId}&date=${deliveryDate}`,
    customerToken,
  );
  await expectStatus(slotsResponse, 200);
  const slots = (await slotsResponse.json()) as Array<{ slotStart: string; available: boolean }>;
  const slotStart = slots.find((slot) => slot.available)?.slotStart;
  expect(slotStart).toBeDefined();
  console.log(`[phase-8-e2e] customer address ${addressId} received an available delivery slot`);

  const quoteResponse = await api(request, "post", "/checkout/quote", customerToken, {
    addressId,
    paymentMethod: "COD",
    items: [{ vendorId: vendorProfileId, slotStart }],
  });
  await expectStatus(quoteResponse, 200);
  const quoteHash = (await quoteResponse.json()).quoteHash as string;
  expect(quoteHash).toBeTruthy();

  const checkoutResponse = await api(request, "post", "/checkout", customerToken, {
    addressId,
    clientOrderId: randomUUID(),
    quoteHash,
    paymentMethod: "COD",
    items: [{ vendorId: vendorProfileId, slotStart }],
  });
  await expectStatus(checkoutResponse, 200);
  const orderId = (await checkoutResponse.json()).orders?.[0]?.orderId as number | undefined;
  expect(orderId).toBeDefined();

  for (const action of ["accept", "prepare", "out-for-delivery", "delivered"]) {
    const response = await api(request, "post", `/vendor/orders/${orderId}/${action}`, vendorToken);
    await expectStatus(response, 200);
  }

  const deliveredOrderResponse = await api(request, "get", `/orders/${orderId}`, customerToken);
  await expectStatus(deliveredOrderResponse, 200);
  expect((await deliveredOrderResponse.json()).status).toBe("DELIVERED");
  console.log(`[phase-8-e2e] customer placed COD order ${orderId}; vendor accepted and delivered it`);

  await signIn(page, customerEmail, accountPassword);
  await page.goto(`/orders/${orderId}/dispute`);
  await page.getByLabel("Reason").selectOption("ITEM_DAMAGED");
  await page.getByLabel("Description").fill("The flowers arrived damaged during this local test.");
  await page.getByRole("button", { name: "Submit dispute" }).click();
  await expect(page.getByRole("heading", { name: "Dispute submitted" })).toBeVisible();
  console.log("[phase-8-e2e] customer raised a dispute in the browser UI");

  const customerDisputesResponse = await api(request, "get", "/disputes?page=0&size=100", customerToken);
  await expectStatus(customerDisputesResponse, 200);
  const customerDisputes = await customerDisputesResponse.json();
  const dispute = customerDisputes.content.find((item: { orderId: number }) => item.orderId === orderId);
  expect(dispute).toBeDefined();
  const disputeId = dispute.id as number;

  const vendorContext = await browser.newContext();
  const vendorPage = await vendorContext.newPage();
  await signIn(vendorPage, vendorEmail, accountPassword);
  await vendorPage.goto("/vendor/disputes");
  await vendorPage.getByRole("button", { name: "Respond" }).first().click();
  await vendorPage.getByLabel("Response").fill("We reviewed the issue and acknowledge the damaged flowers.");
  await vendorPage.getByRole("button", { name: "Submit response" }).click();
  await expect(vendorPage.getByRole("button", { name: "Respond" })).toHaveCount(0);

  const vendorDisputeResponse = await api(request, "get", `/vendor/disputes/${disputeId}`, vendorToken);
  await expectStatus(vendorDisputeResponse, 200);
  expect((await vendorDisputeResponse.json()).status).toBe("UNDER_REVIEW");
  console.log(`[phase-8-e2e] vendor responded to dispute ${disputeId} in the browser UI`);

  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  await signIn(adminPage, ADMIN_EMAIL, ADMIN_PASSWORD);
  await adminPage.goto("/admin/disputes");
  await adminPage.getByLabel("Status").selectOption("UNDER_REVIEW");
  await adminPage.getByRole("button", { name: "Resolve" }).first().click();
  await adminPage.getByLabel("Outcome").selectOption("RESOLVED_PARTIAL");
  await adminPage.getByLabel("Refund amount (INR)").fill("30.00");
  await adminPage.getByLabel("Resolution note").fill("Partial refund approved for the damaged flowers.");
  await adminPage.getByRole("button", { name: "Resolve dispute" }).click();
  await expect(adminPage.getByRole("button", { name: "Resolve" })).toHaveCount(0);

  const adminDisputeResponse = await api(request, "get", `/admin/disputes/${disputeId}`, adminToken);
  await expectStatus(adminDisputeResponse, 200);
  const resolvedDispute = await adminDisputeResponse.json();
  expect(resolvedDispute.status).toBe("RESOLVED_PARTIAL");
  expect(Number(resolvedDispute.refundAmount)).toBe(30);
  console.log(`[phase-8-e2e] admin resolved dispute ${disputeId} as partial refund in the browser UI`);

  await page.goto(`/disputes/${disputeId}`);
  await expect(page.getByText(/resolved.*partial refund/i)).toBeVisible();
  await expect(page.getByText(/30\.00/)).toBeVisible();
  console.log("[phase-8-e2e] customer viewed the resolved status and refund amount");

  await page.goto(`/orders/${orderId}/dispute`);
  await page.getByLabel("Reason").selectOption("ITEM_DAMAGED");
  await page.getByLabel("Description").fill("A second dispute attempt must be rejected.");
  await page.getByRole("button", { name: "Submit dispute" }).click();
  await expect(page.getByText(/already|existing|duplicate/i)).toBeVisible();

  const duplicateResponse = await api(request, "post", `/disputes/orders/${orderId}`, customerToken, {
    reasonCode: "ITEM_DAMAGED",
    description: "Confirm the API rejects a second dispute.",
  });
  await expectStatus(duplicateResponse, 409);
  console.log("[phase-8-e2e] second dispute attempt was blocked in the UI and returned API 409");

  console.log(`[phase-8-e2e] completed orderId=${orderId} disputeId=${disputeId} outcome=${resolvedDispute.status} refundAmount=${resolvedDispute.refundAmount}`);
  await vendorContext.close();
  await adminContext.close();
});
