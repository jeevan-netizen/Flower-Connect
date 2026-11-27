import { expect, test, type APIRequestContext } from "@playwright/test";
import type { PassbookEntry } from "@/features/farmer/types";

const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@flowerconnect.test";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? "AdminPass123!";

async function api(
  request: APIRequestContext,
  method: "get" | "post",
  path: string,
  token?: string,
  data?: unknown,
) {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const url = `${API_BASE}${path}`;
  if (method === "get") return request.get(url, { headers });
  return request.post(url, { headers, data });
}

async function expectStatus(response: { status(): number; text(): Promise<string> }, expected: number) {
  const responseText = response.status() === expected ? "" : await response.text();
  expect(response.status(), responseText).toBe(expected);
}

async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const response = await api(request, "post", "/auth/login", undefined, { email, password });
  await expectStatus(response, 200);
  return (await response.json()).accessToken as string;
}

test("farmer module: register, join, deposits, collections, suspension", async ({ request }) => {
  test.setTimeout(120000);

  // --- Setup ---
  const adminToken = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);

  // Vendor registration
  const vendorSuffix = Date.now();
  const vendorEmail = `vendor-${vendorSuffix}@flowerconnect.test`;
  const vendorRegister = await api(
    request,
    "post",
    "/vendors/register",
    undefined,
    {
      email: vendorEmail,
      password: "VendorPass123!",
      fullName: "E2e Vendor Owner",
      phone: `+919000000${vendorSuffix % 1000}`,
      businessName: "Green Acres Florist",
      addressLine1: "123 MG Road",
      serviceLocationId: 1,
      deliveryRadiusKm: 5.00,
      prepTimeMinutes: 30,
      slotDurationMinutes: 60,
      maxOrdersPerSlot: 10,
      hours: [
        { weekday: "MONDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "TUESDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "WEDNESDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "THURSDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "FRIDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "SATURDAY", openTime: "09:00", closeTime: "18:00", closed: false },
        { weekday: "SUNDAY", openTime: "09:00", closeTime: "18:00", closed: false },
      ],
    },
  );
  await expectStatus(vendorRegister, 201);
  const vendorId = (await vendorRegister.json()).id as number;

  // Admin approves the vendor
  await expectStatus(
    await api(request, "post", `/admin/vendors/${vendorId}/approve`, adminToken, {}),
    200,
  );

  const vendorToken = await apiLogin(request, vendorEmail, "VendorPass123!");

  // Farmer registration
  const farmerSuffix = Date.now();
  const farmerEmail = `farmer-${farmerSuffix}@flowerconnect.test`;
  const farmerRegister = await api(
    request,
    "post",
    "/farmer/register",
    undefined,
    {
      email: farmerEmail,
      password: "FarmerPass123!",
      fullName: "E2e Farmer",
      phone: `+919000001${farmerSuffix % 1000}`,
    },
  );
  await expectStatus(farmerRegister, 201);
  const farmerToken = await apiLogin(request, farmerEmail, "FarmerPass123!");

  let linkId: number | null = null;

  try {
    // ============ 1. Farmer requests to join vendor ============
    const registerLink = await api(
      request,
      "post",
      "/farmer/links",
      farmerToken,
      { vendorId },
    );
    await expectStatus(registerLink, 201);
    const createdLink = await registerLink.json();
    linkId = createdLink.id as number;
    expect(createdLink.status).toBe("PENDING");

    // ============ 2. Vendor accepts link ============
    const accept = await api(
      request,
      "post",
      `/vendor/farmer-links/${linkId}/accept`,
      vendorToken,
      {},
    );
    await expectStatus(accept, 200);
    expect((await accept.json()).status).toBe("ACTIVE");

    // ============ 3. Vendor adds three deposits ============
    const deposits = [
      { flowerType: "Roses", grossKg: 32, pricePerKg: 100, deduction: 3, net: 29, amount: 2900 },
      { flowerType: "Lilies", grossKg: 60, pricePerKg: 50, deduction: 5, net: 55, amount: 2750 },
      { flowerType: "Tulips", grossKg: 20, pricePerKg: 100, deduction: 2, net: 18, amount: 1800 },
    ];

    for (const d of deposits) {
      await api(
        request,
        "post",
        `/vendor/farmer-links/${linkId}/deposits`,
        vendorToken,
        { flowerType: d.flowerType, grossKg: d.grossKg, pricePerKg: d.pricePerKg },
      );
    }

    const totalDeposits = deposits.reduce((sum, d) => sum + d.amount, 0);

    // ============ 4. Farmer views passbook ============
    const passbook = await api(
      request,
      "get",
      `/farmer/links/${linkId}/passbook`,
      farmerToken,
    );
    await expectStatus(passbook, 200);
    const passbookData = await passbook.json();
    expect(passbookData.linkId).toBe(linkId);
    expect(passbookData.outstandingBalance).toBe(totalDeposits);
    expect(passbookData.entries.length).toBe(deposits.length);

    // Verify each deposit entry
    for (const d of deposits) {
      const entry = passbookData.entries.find((e: PassbookEntry) => e.id !== undefined && e.type === "DEPOSIT" && e.amount === d.amount);
      expect(entry).toBeTruthy();
      expect(entry.grossKg).toBe(d.grossKg);
      expect(entry.deductionKg).toBe(d.deduction);
      expect(entry.netKg).toBe(d.net);
      expect(entry.amount).toBe(d.amount);
    }

    // ============ 5. Farmer requests collection ============
    const collectionRequest = await api(
      request,
      "post",
      `/farmer/links/${linkId}/collections`,
      farmerToken,
      { amount: 1000, mode: "UPI", note: "Weekly collection" },
    );
    await expectStatus(collectionRequest, 201);
    const collection = await collectionRequest.json();
    expect(collection.status).toBe("PENDING");
    expect(collection.amount).toBe(1000);

    // Verify balance unchanged after pending collection
    const passbook2 = await api(
      request,
      "get",
      `/farmer/links/${linkId}/passbook`,
      farmerToken,
    );
    await expectStatus(passbook2, 200);
    const passbookData2 = await passbook2.json();
    expect(passbookData2.outstandingBalance).toBe(totalDeposits);

    // ============ 6. Vendor confirms collection ============
    const confirm = await api(
      request,
      "post",
      `/vendor/farmer-collections/${collection.id}/confirm`,
      vendorToken,
      {},
    );
    await expectStatus(confirm, 200);
    expect((await confirm.json()).status).toBe("CONFIRMED");

    // Verify balance dropped
    const passbook3 = await api(
      request,
      "get",
      `/farmer/links/${linkId}/passbook`,
      farmerToken,
    );
    await expectStatus(passbook3, 200);
    const passbookData3 = await passbook3.json();
    expect(passbookData3.outstandingBalance).toBe(totalDeposits - 1000);

    // ============ 7. Vendor suspends link ============
    const suspend = await api(
      request,
      "post",
      `/vendor/farmer-links/${linkId}/suspend`,
      vendorToken,
      {},
    );
    await expectStatus(suspend, 200);
    expect((await suspend.json()).status).toBe("SUSPENDED");

    // ============ 8. Suspension blocks new deposits ============
    const blockedDeposit = await api(
      request,
      "post",
      `/vendor/farmer-links/${linkId}/deposits`,
      vendorToken,
      { flowerType: "Orchids", grossKg: 10, pricePerKg: 100 },
    );
    expect(blockedDeposit.status()).toBe(409);

    // ============ 9. Suspension blocks new collection requests ============
    const blockedCollection = await api(
      request,
      "post",
      `/farmer/links/${linkId}/collections`,
      farmerToken,
      { amount: 100, mode: "CASH" },
    );
    expect(blockedCollection.status()).toBe(409);

    // ============ 10. Passbook still readable after suspension ============
    const passbook4 = await api(
      request,
      "get",
      `/farmer/links/${linkId}/passbook`,
      farmerToken,
    );
    await expectStatus(passbook4, 200);
    const passbookData4 = await passbook4.json();
    expect(passbookData4.linkId).toBe(linkId);
    expect(passbookData4.linkStatus).toBe("SUSPENDED");
    expect(passbookData4.outstandingBalance).toBe(totalDeposits - 1000);
  } finally {
    // No browser context to close - API-only test
  }
});
