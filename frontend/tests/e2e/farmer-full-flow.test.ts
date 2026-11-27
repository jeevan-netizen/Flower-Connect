import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const APP_BASE = "http://localhost:5173";
const API_BASE = "http://localhost:8080/api/v1";
const SCREENSHOT_DIR = "C:\\Users\\jeeva\\AppData\\Local\\Temp\\flowerconnect-demo-screenshots\\farmer";

const FARMER = { email: "farmer.demo@flowerconnect.test", password: "FarmerDemo123!" };
const ADMIN = { email: "admin@flowerconnect.local", password: "flowerconnect123" };

async function uiLogin(ctx: BrowserContext, email: string, password: string): Promise<Page> {
  const page = await ctx.newPage();
  await page.goto(`${APP_BASE}/login`);
  await page.waitForLoadState("domcontentloaded");
  await page.fill("input#email", email);
  await page.fill("input#password", password);
  await page.click('button:has-text("Sign in")');
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });
  return page;
}

test.describe("Full Farmer & Passbook Demo Flow Verification", () => {
  test.setTimeout(300000);

  test("Farmer lifecycle: join vendor, deposits/deductions, passbook, collections, suspend/reactivate, isolation", async ({ browser, request }) => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

    // ------------------------------------------------------------------------
    // Setup: Register a fresh 3rd vendor and approve via Admin so we can test
    // "search vendors, request to join, accept through vendor UI" end-to-end
    // on a fresh link starting at ₹0.00 balance.
    // ------------------------------------------------------------------------
    const suffix = Date.now().toString().slice(-6);
    const newVendorEmail = `fresh.florist.${suffix}@flowerconnect.test`;
    const newVendorPassword = "FreshVendor123!";
    const newVendorName = `Fresh Florist ${suffix}`;
    const newVendorPhone = `98${suffix}11`;

    const locationId = 1;

    // Register vendor
    const regVendorRes = await request.post(`${API_BASE}/vendors/register`, {
      data: {
        email: newVendorEmail,
        password: newVendorPassword,
        fullName: "Fresh Vendor Owner",
        phone: newVendorPhone,
        businessName: newVendorName,
        addressLine1: "123 Rose Lane",
        serviceLocationId: locationId,
        latitude: 12.9352,
        longitude: 77.6245,
        deliveryRadiusKm: 10,
      },
    });
    expect(regVendorRes.ok()).toBe(true);
    const registeredVendor = await regVendorRes.json();
    const newVendorId = registeredVendor.id;

    // Login as Admin and approve the new vendor
    const adminLoginRes = await request.post(`${API_BASE}/auth/login`, {
      data: { email: ADMIN.email, password: ADMIN.password },
    });
    expect(adminLoginRes.ok()).toBe(true);
    const adminToken = (await adminLoginRes.json()).accessToken;

    const approveRes = await request.post(`${API_BASE}/admin/vendors/${newVendorId}/approve`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(approveRes.ok()).toBe(true);
    console.log(`[Farmer] Registered and approved fresh vendor: ${newVendorName} (id=${newVendorId})`);

    // Register a 2nd farmer for passbook isolation test
    const otherFarmerEmail = `other.farmer.${suffix}@flowerconnect.test`;
    const otherFarmerPhone = `97${suffix}22`;
    const regOtherFarmerRes = await request.post(`${API_BASE}/farmer/register`, {
      data: {
        email: otherFarmerEmail,
        password: "OtherFarmer123!",
        fullName: "Other Farmer",
        phone: otherFarmerPhone,
      },
    });
    expect(regOtherFarmerRes.ok()).toBe(true);
    const otherFarmerLoginRes = await request.post(`${API_BASE}/auth/login`, {
      data: { email: otherFarmerEmail, password: "OtherFarmer123!" },
    });
    const otherFarmerToken = (await otherFarmerLoginRes.json()).accessToken;
    const otherLinkRes = await request.post(`${API_BASE}/farmer/links`, {
      headers: { Authorization: `Bearer ${otherFarmerToken}` },
      data: { vendorId: newVendorId },
    });
    expect(otherLinkRes.ok()).toBe(true);
    const otherFarmerLinkId = (await otherLinkRes.json()).id;
    console.log(`[Farmer] Created other farmer link id=${otherFarmerLinkId} for isolation check`);

    // ------------------------------------------------------------------------
    // Browser Contexts: Seeded Farmer and Fresh Vendor
    // ------------------------------------------------------------------------
    const farmerCtx = await browser.newContext();
    const vendorCtx = await browser.newContext();
    const farmer = await uiLogin(farmerCtx, FARMER.email, FARMER.password);
    const vendor = await uiLogin(vendorCtx, newVendorEmail, newVendorPassword);

    // ------------------------------------------------------------------------
    // 1. Farmer searches vendors, requests to join; Vendor accepts through UI
    // ------------------------------------------------------------------------
    await farmer.goto(`${APP_BASE}/farmer/vendors`);
    await expect(farmer.getByRole("heading", { name: /Find a florist/i })).toBeVisible({ timeout: 15000 });
    await farmer.getByPlaceholder(/Search by business name/i).fill(newVendorName);
    await farmer.waitForTimeout(600);

    await expect(farmer.getByText(newVendorName).first()).toBeVisible({ timeout: 10000 });
    await farmer.getByRole("button", { name: /Request to join/i }).first().click();
    await expect(farmer.getByRole("button", { name: /Requested/i }).first()).toBeVisible({ timeout: 10000 });
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "01-farmer-requested-join.png"), fullPage: true });

    // Vendor accepts the request in /vendor/farmers
    await vendor.goto(`${APP_BASE}/vendor/farmers`);
    await expect(vendor.getByRole("heading", { name: /Linked farmers/i })).toBeVisible({ timeout: 15000 });
    const farmerRow = vendor.locator("table tbody tr", { hasText: "Demo Farmer" }).first();
    await expect(farmerRow).toBeVisible({ timeout: 10000 });
    await farmerRow.getByRole("button", { name: "Accept" }).click();
    await expect(farmerRow.getByText("ACTIVE")).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "02-vendor-accepted-farmer.png"), fullPage: true });

    // Read linkId from the farmer link in vendor table
    const linkHref = await farmerRow.getByRole("link", { name: /Demo Farmer/i }).getAttribute("href");
    const linkId = linkHref!.split("/").slice(-2, -1)[0];
    console.log(`[Farmer] Accepted link id=${linkId}`);

    // ------------------------------------------------------------------------
    // 2. Vendor adds 4 deposits @ ₹100/kg; verify live preview, saved row, backend
    // ------------------------------------------------------------------------
    await vendor.goto(`${APP_BASE}/vendor/farmer-links/${linkId}/passbook`);
    await expect(vendor.getByRole("heading", { name: /Passbook —/i })).toBeVisible({ timeout: 15000 });

    const depositCases = [
      { flower: "Red Roses", gross: "9.5", deduction: "0.00 kg", net: "9.50 kg", amount: "₹950.00", expectedNet: 9.5, expectedDeduction: 0, expectedAmount: 950 },
      { flower: "White Lilies", gross: "20", deduction: "2.00 kg", net: "18.00 kg", amount: "₹1,800.00", expectedNet: 18, expectedDeduction: 2, expectedAmount: 1800 },
      { flower: "Yellow Tulips", gross: "32", deduction: "3.00 kg", net: "29.00 kg", amount: "₹2,900.00", expectedNet: 29, expectedDeduction: 3, expectedAmount: 2900 },
      { flower: "Purple Orchids", gross: "55", deduction: "5.00 kg", net: "50.00 kg", amount: "₹5,000.00", expectedNet: 50, expectedDeduction: 5, expectedAmount: 5000 },
    ];

    for (const c of depositCases) {
      await vendor.locator("input#flowerType").fill(c.flower);
      await vendor.locator("input#grossKg").fill(c.gross);
      await vendor.locator("input#pricePerKg").fill("100");

      // Verify live preview
      await expect(vendor.getByText(c.deduction).first()).toBeVisible({ timeout: 5000 });
      await expect(vendor.getByText(c.net).first()).toBeVisible({ timeout: 5000 });
      await expect(vendor.getByText(c.amount).first()).toBeVisible({ timeout: 5000 });

      const submitBtn = vendor.getByRole("button", { name: /Record deposit/i });
      await expect(submitBtn).toBeEnabled({ timeout: 5000 });
      await submitBtn.click();
      await vendor.waitForTimeout(600);
    }

    await expect(vendor.getByText("₹10,650.00").first()).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "03-vendor-deposits-recorded.png"), fullPage: true });

    // Verify backend passbook values via API
    const vendorToken = await vendor.evaluate(() => {
      const raw = localStorage.getItem("fc-auth");
      return raw ? (JSON.parse(raw)?.state?.accessToken ?? "") : "";
    });
    const backendPassbookRes = await request.get(`${API_BASE}/vendor/farmer-links/${linkId}/passbook`, {
      headers: { Authorization: `Bearer ${vendorToken}` },
    });
    expect(backendPassbookRes.ok()).toBe(true);
    const backendPassbook = await backendPassbookRes.json();
    expect(Number(backendPassbook.outstandingBalance)).toBe(10650);
    for (const c of depositCases) {
      const entry = backendPassbook.entries.find((e: { flowerType: string }) => e.flowerType === c.flower);
      expect(entry).toBeTruthy();
      expect(Number(entry.deductionKg)).toBe(c.expectedDeduction);
      expect(Number(entry.netKg)).toBe(c.expectedNet);
      expect(Number(entry.amount)).toBe(c.expectedAmount);
    }
    console.log("[Farmer] Backend passbook values verified for all 4 deposits");

    // ------------------------------------------------------------------------
    // 3. Farmer opens passbook: verify gross, deduction, net, rate, amount,
    //    running balance; check second vendor's passbook has separate entries
    // ------------------------------------------------------------------------
    await farmer.goto(`${APP_BASE}/farmer/links/${linkId}/passbook`);
    await expect(farmer.getByRole("heading", { name: `Passbook — ${newVendorName}` })).toBeVisible({ timeout: 15000 });
    await expect(farmer.getByText("₹10,650.00").first()).toBeVisible({ timeout: 10000 });

    const expectedRunningBalances = [
      { flower: "Red Roses", gross: "9.50 kg", deduction: "0.00 kg", net: "9.50 kg", rate: "₹100.00/kg", amount: "+₹950.00", running: "₹950.00" },
      { flower: "White Lilies", gross: "20.00 kg", deduction: "2.00 kg", net: "18.00 kg", rate: "₹100.00/kg", amount: "+₹1,800.00", running: "₹2,750.00" },
      { flower: "Yellow Tulips", gross: "32.00 kg", deduction: "3.00 kg", net: "29.00 kg", rate: "₹100.00/kg", amount: "+₹2,900.00", running: "₹5,650.00" },
      { flower: "Purple Orchids", gross: "55.00 kg", deduction: "5.00 kg", net: "50.00 kg", rate: "₹100.00/kg", amount: "+₹5,000.00", running: "₹10,650.00" },
    ];

    for (const r of expectedRunningBalances) {
      const row = farmer.locator("table tbody tr", { hasText: r.flower }).first();
      await expect(row).toBeVisible();
      await expect(row).toContainText(r.gross);
      await expect(row).toContainText(r.deduction);
      await expect(row).toContainText(r.net);
      await expect(row).toContainText(r.rate);
      await expect(row).toContainText(r.amount);
      await expect(row).toContainText(r.running);
    }
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "04-farmer-passbook-entries.png"), fullPage: true });

    // Check second vendor's passbook (seeded link 2: Demo Buyer Florist) has separate entries and balance
    await farmer.goto(`${APP_BASE}/farmer/links/2/passbook`);
    await expect(farmer.getByRole("heading", { name: /Passbook — Demo Buyer Florist/i })).toBeVisible({ timeout: 15000 });
    await expect(farmer.getByText("Red Roses")).not.toBeVisible();
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "05-second-vendor-passbook-isolated.png"), fullPage: true });

    // Return to our test vendor passbook
    await farmer.goto(`${APP_BASE}/farmer/links/${linkId}/passbook`);
    await expect(farmer.getByText("₹10,650.00").first()).toBeVisible({ timeout: 15000 });

    // ------------------------------------------------------------------------
    // 4. Collection above balance refused; valid collection pending (balance unchanged)
    // ------------------------------------------------------------------------
    await farmer.locator("input#amount").fill("11000");
    await farmer.getByRole("button", { name: /Request collection/i }).click();
    await expect(farmer.getByRole("alert")).toBeVisible({ timeout: 10000 });
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "06-collection-above-balance-refused.png"), fullPage: true });

    // Request valid collection (₹1,000.00)
    await farmer.locator("input#amount").fill("1000");
    await farmer.getByRole("button", { name: /Request collection/i }).click();
    await expect(farmer.getByText("PENDING").first()).toBeVisible({ timeout: 10000 });
    // Balance must still be ₹10,650.00
    await expect(farmer.getByText("₹10,650.00").first()).toBeVisible();
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "07-collection-pending-balance-unchanged.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // 5. Vendor confirms collection -> balance decreases on both screens;
    //    Repeat with rejected collection -> balance unchanged
    // ------------------------------------------------------------------------
    await vendor.goto(`${APP_BASE}/vendor/farmer-links/${linkId}/passbook`);
    await expect(vendor.getByRole("button", { name: "Confirm" }).first()).toBeVisible({ timeout: 15000 });
    await vendor.getByRole("button", { name: "Confirm" }).first().click();
    await expect(vendor.getByText("₹9,650.00").first()).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "08-vendor-confirmed-collection.png"), fullPage: true });

    // Farmer screen shows ₹9,650.00 and CONFIRMED
    await farmer.reload();
    await expect(farmer.getByText("₹9,650.00").first()).toBeVisible({ timeout: 15000 });
    await expect(farmer.getByText("CONFIRMED").first()).toBeVisible();
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "09-farmer-confirmed-collection.png"), fullPage: true });

    // Farmer requests ₹500 collection -> Vendor rejects -> balance stays ₹9,650.00
    await farmer.locator("input#amount").fill("500");
    await farmer.getByRole("button", { name: /Request collection/i }).click();
    await expect(farmer.getByText("PENDING").first()).toBeVisible({ timeout: 10000 });

    await vendor.reload();
    await expect(vendor.getByRole("button", { name: "Reject" }).first()).toBeVisible({ timeout: 15000 });
    await vendor.getByRole("button", { name: "Reject" }).first().click();
    await expect(vendor.getByText("₹9,650.00").first()).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "10-vendor-rejected-collection.png"), fullPage: true });

    await farmer.reload();
    await expect(farmer.getByText("₹9,650.00").first()).toBeVisible({ timeout: 15000 });
    await expect(farmer.getByText("REJECTED").first()).toBeVisible();

    // ------------------------------------------------------------------------
    // 6. Suspend farmer -> deposits & collection requests blocked with readable
    //    message, passbook visible; Reactivate -> deposits work again
    // ------------------------------------------------------------------------
    await vendor.goto(`${APP_BASE}/vendor/farmers`);
    await vendor.locator("table tbody tr", { hasText: "Demo Farmer" }).getByRole("button", { name: "Suspend" }).click();
    await expect(vendor.locator("table tbody tr", { hasText: "Demo Farmer" }).getByText("SUSPENDED")).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "11-vendor-suspended-farmer.png"), fullPage: true });

    // Vendor attempts deposit while suspended -> readable error
    await vendor.goto(`${APP_BASE}/vendor/farmer-links/${linkId}/passbook`);
    await vendor.locator("input#flowerType").fill("SuspendedFlower");
    await vendor.locator("input#grossKg").fill("10");
    await vendor.locator("input#pricePerKg").fill("100");
    await vendor.getByRole("button", { name: /Record deposit/i }).click();
    await expect(vendor.getByRole("alert")).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "12-vendor-deposit-blocked-suspended.png"), fullPage: true });

    // Farmer sees passbook visible, collection blocked with readable message
    await farmer.reload();
    await expect(farmer.getByText(/suspended/i).first()).toBeVisible({ timeout: 10000 });
    await expect(farmer.getByRole("button", { name: /Cannot request/i })).toBeDisabled();
    await expect(farmer.getByText("Red Roses").first()).toBeVisible();
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "13-farmer-passbook-visible-while-suspended.png"), fullPage: true });

    // Vendor reactivates farmer
    await vendor.goto(`${APP_BASE}/vendor/farmers`);
    await vendor.locator("table tbody tr", { hasText: "Demo Farmer" }).getByRole("button", { name: "Reactivate" }).click();
    await expect(vendor.locator("table tbody tr", { hasText: "Demo Farmer" }).getByText("ACTIVE")).toBeVisible({ timeout: 10000 });

    // Deposit works again (10 kg @ 100 = 900 -> balance becomes 10,550)
    await vendor.goto(`${APP_BASE}/vendor/farmer-links/${linkId}/passbook`);
    await vendor.locator("input#flowerType").fill("Marigold");
    await vendor.locator("input#grossKg").fill("10");
    await vendor.locator("input#pricePerKg").fill("100");
    await vendor.getByRole("button", { name: /Record deposit/i }).click();
    await expect(vendor.getByText("₹10,550.00").first()).toBeVisible({ timeout: 10000 });
    await vendor.screenshot({ path: path.join(SCREENSHOT_DIR, "14-deposit-works-after-reactivate.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // 7. Foreign passbook access refused (another farmer's linkId + invalid ID)
    // ------------------------------------------------------------------------
    await farmer.goto(`${APP_BASE}/farmer/links/${otherFarmerLinkId}/passbook`);
    await expect(farmer.getByText(/access|forbidden|permission|not found|denied/i).first()).toBeVisible({ timeout: 10000 });
    await farmer.screenshot({ path: path.join(SCREENSHOT_DIR, "15-foreign-passbook-refused.png"), fullPage: true });

    await farmerCtx.close();
    await vendorCtx.close();
  });
});
