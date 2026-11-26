import { randomBytes, randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const ADMIN_EMAIL = "e2e-admin@flowerconnect.test";
const ADMIN_PASSWORD = "AdminPass123!";

if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  throw new Error("Set ADMIN_EMAIL and ADMIN_PASSWORD for the local Playwright run.");
}

async function api(
  request: APIRequestContext,
  method: "get" | "post" | "put" | "patch",
  path: string,
  token?: string,
  data?: unknown,
) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const url = `${API_BASE}${path}`;
  if (method === "get") return request.get(url, { headers });
  if (method === "patch") return request.patch(url, { headers, data });
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

async function signIn(page: Page, email: string, password: string) {
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  // Clear persisted session so the store re-hydrates as unauthenticated
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.reload();
  await page.waitForLoadState("networkidle");
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/$/);
}

test("customer submits review, vendor replies, admin moderates", async ({ page, context }) => {
  test.setTimeout(240_000);

  const request = context.request;
  const accountPassword = `${randomBytes(32).toString("base64url")}Aa1!`;
  const suffix = randomUUID().slice(0, 8);
  const customerEmail = `e2e-review-cust-${suffix}@flowerconnect.test`;
  const vendorEmail = `e2e-review-vend-${suffix}@flowerconnect.test`;

  // ------------------------------------------------------------------
  // 1. API setup: create customer, vendor, approve vendor, product,
  // delivered order
  // ------------------------------------------------------------------

  const locationsResponse = await api(request, "get", "/locations");
  await expectStatus(locationsResponse, 200);
  const locations = await locationsResponse.json();
  const serviceLocationId = locations.flatMap((city: { areas?: Array<{ id: number }> }) => city.areas ?? [])[0]?.id;
  expect(serviceLocationId).toBeDefined();

  // Register customer
  const customerReg = await api(request, "post", "/auth/register", undefined, {
    email: customerEmail,
    password: accountPassword,
    fullName: "Review E2E Customer",
    phone: `+91${Date.now().toString().slice(-10)}`,
  });
  await expectStatus(customerReg, 200);
  const customerToken = await apiLogin(request, customerEmail, accountPassword);

  // Register vendor with operating hours (creates PENDING_APPROVAL profile)
  const vendorReg = await api(request, "post", "/vendors/register", undefined, {
    email: vendorEmail,
    password: accountPassword,
    fullName: "Review E2E Vendor",
    phone: `+918${Date.now().toString().slice(-9)}`,
    businessName: `Review E2E Florist ${suffix}`,
    description: "Temporary local end-to-end test vendor",
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
  const vendorToken = await apiLogin(request, vendorEmail, accountPassword);

  // Approve the vendor via API as admin
  const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
  // Find the vendor profile id for this vendor (walk all pages)
  let adminVendorProfileId: number | null = null;
  let vendorPage = 0;
  while (adminVendorProfileId === null) {
    const vendorsResponse = await api(request, "get", `/admin/vendors?page=${vendorPage}&size=100`, adminToken);
    await expectStatus(vendorsResponse, 200);
    const vendorsBody = await vendorsResponse.json();
    const vendorProfile = vendorsBody.content.find((v: { ownerEmail: string }) => v.ownerEmail === vendorEmail);
    if (vendorProfile) {
      adminVendorProfileId = vendorProfile.id;
      break;
    }
    if (vendorsBody.last) break;
    vendorPage++;
  }
  expect(adminVendorProfileId).toBeDefined();
  const approveRes = await api(request, "post", `/admin/vendors/${adminVendorProfileId}/approve`, adminToken);
  await expectStatus(approveRes, 200);

  // Find an active category first (product creation requires categoryId)
  const catsResponse = await api(request, "get", "/categories");
  await expectStatus(catsResponse, 200);
  const cats = await catsResponse.json();
  const categoryId = cats.content?.[0]?.id ?? cats[0]?.id;
  expect(categoryId).toBeDefined();

  // Create product as ACTIVE so it's purchasable by the customer
  const productRes = await api(request, "post", "/vendors/products", vendorToken, {
    name: `Review E2E Rose ${suffix}`,
    categoryId,
    status: "ACTIVE",
    description: "Temporary test product",
    basePrice: 100.00,
  });
  await expectStatus(productRes, 201);
  const productId = (await productRes.json()).id;

  // Add stock so the product is purchasable
  const stockInRes = await api(request, "post", `/vendors/products/${productId}/inventory/stock-in`, vendorToken, {
    quantity: 10,
    reason: "E2E test stock",
  });
  await expectStatus(stockInRes, 200);

  // Checkout - add item to cart
  const cartAddRes = await api(request, "post", "/cart/items", customerToken, {
    productId,
    quantity: 1,
  });
  await expectStatus(cartAddRes, 201);

  // Create a customer address (required for checkout quote)
  const addressRes = await api(request, "post", "/addresses", customerToken, {
    label: "Home",
    line1: "123 Test Street",
    serviceLocationId,
  });
  await expectStatus(addressRes, 201);
  const addressBody = await addressRes.json();
  const addressId = addressBody.id;

  // Get vendor profile for vendorId
  const vendorProfileRes = await api(request, "get", "/vendors/profile", vendorToken);
  await expectStatus(vendorProfileRes, 200);
  const vendorProfileBody = await vendorProfileRes.json();
  const vendorProfileId = vendorProfileBody.id;

  // Get available slots for tomorrow
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dateStr = tomorrow.toISOString().slice(0, 10);
  const slotsRes = await api(request, "get", `/vendors/${vendorProfileId}/slots?addressId=${addressId}&date=${dateStr}&email=${customerEmail}`, customerToken);
  await expectStatus(slotsRes, 200);
  const slots = await slotsRes.json();
  expect(slots.length).toBeGreaterThan(0);
  const slotStart = slots[0].slotStart;

  // Get checkout quote
  const quoteRes = await api(request, "post", "/checkout/quote", customerToken, {
    paymentMethod: "COD",
    addressId,
    items: [{ vendorId: vendorProfileId, slotStart }],
  });
  await expectStatus(quoteRes, 200);
  const quoteBody = await quoteRes.json();
  const quoteHash = quoteBody.quoteHash;
  expect(quoteHash).toBeDefined();

  // Checkout
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

  // Vendor accepts the order
  const acceptRes = await api(request, "post", `/vendor/orders/${orderId}/accept`, vendorToken);
  await expectStatus(acceptRes, 200);

  // Vendor prepares the order
  const prepareRes = await api(request, "post", `/vendor/orders/${orderId}/prepare`, vendorToken);
  await expectStatus(prepareRes, 200);

  // Vendor marks out-for-delivery
  const outForDeliveryRes = await api(request, "post", `/vendor/orders/${orderId}/out-for-delivery`, vendorToken);
  await expectStatus(outForDeliveryRes, 200);

  // Vendor delivers the order
  const deliverRes = await api(request, "post", `/vendor/orders/${orderId}/delivered`, vendorToken);
  await expectStatus(deliverRes, 200);

  // Verify order is DELIVERED
  const orderRes = await api(request, "get", `/orders/${orderId}`, customerToken);
  await expectStatus(orderRes, 200);
  const orderBody = await orderRes.json();
  expect(orderBody.status).toBe("DELIVERED");

  // Store orderId for later
  const reviewOrderId = orderId;

  // ------------------------------------------------------------------
  // 2. Browser: customer submits a review
  // ------------------------------------------------------------------

  await signIn(page, customerEmail, accountPassword);

  // Navigate to order detail
  await page.goto(`/orders/${reviewOrderId}`);
  await expect(page.getByText("Rate your experience")).toBeVisible();

  // Select 5 stars and submit
  await page.getByLabel("Rate 5 of 5").click();
  await page.getByRole("button", { name: "Submit review" }).click();
  await expect(page.getByText("Thank you for your review!")).toBeVisible();

  // ------------------------------------------------------------------
  // 3. Browser: verify storefront shows the review and rating
  // ------------------------------------------------------------------

  // Get vendor profile id for storefront URL (already fetched during checkout setup)

  await page.goto(`/vendors/${vendorProfileId}`);
  await expect(page.getByText("Customer reviews")).toBeVisible();
  await expect(page.getByText("5.0 stars (1 review)").first()).toBeVisible();

  // ------------------------------------------------------------------
  // 4. Browser: vendor replies to the review
  // ------------------------------------------------------------------

  await signIn(page, vendorEmail, accountPassword);
  await page.goto("/vendor/reviews");
  await expect(page.getByText("Your rating")).toBeVisible();

  // Find the review and reply
  await page.getByPlaceholder("Write a reply…").fill("Thank you for your feedback!");
  await page.getByRole("button", { name: "Post reply" }).click();
  await expect(page.getByText("Thank you for your feedback!")).toBeVisible();

  // ------------------------------------------------------------------
  // 5. Browser: admin hides and unhides the review
  // ------------------------------------------------------------------

  await signIn(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.goto("/admin/reviews");
  await expect(page.getByRole("heading", { name: "Review moderation" })).toBeVisible();

  // Hide the review
  await page.evaluate(() => {
    // Mock window.prompt for the hide dialog
    window.prompt = () => "Spam content";
  });
  await page.getByRole("button", { name: "Hide" }).first().click();
  await page.waitForTimeout(1000);
  // Review should now show as Hidden on the admin page
  await expect(page.getByText("Hidden").last()).toBeVisible();

  // ------------------------------------------------------------------
  // 6. Verify DB aggregates
  // ------------------------------------------------------------------

  const summaryRes = await api(request, "get", "/vendors/reviews/summary", vendorToken);
  await expectStatus(summaryRes, 200);
  const summary = await summaryRes.json();
  expect(summary.reviewCount).toBe(1);
  expect(summary.avgRating).toBe(5.0);
});
