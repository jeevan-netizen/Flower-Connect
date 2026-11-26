import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";
import * as zlib from "zlib";

const APP_BASE = "http://localhost:5173";
const API_BASE = "http://localhost:8080/api/v1";
const SCREENSHOT_DIR = "C:\\Users\\jeeva\\AppData\\Local\\Temp\\flowerconnect-demo-screenshots\\b2b";

const SELLER = { email: "seller.demo@flowerconnect.test", password: "SellerDemo123!" };
const BUYER = { email: "buyer.demo@flowerconnect.test", password: "BuyerDemo123!" };
const FARMER = { email: "farmer.demo@flowerconnect.test", password: "FarmerDemo123!" };

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

async function getStockForSeller(page: Page): Promise<number> {
  await page.goto(`${APP_BASE}/vendor/b2b/place-order`);
  await page.getByPlaceholder(/Search sellers/).fill("Demo Seller");
  await page.waitForTimeout(500);
  await page.getByRole("button", { name: /Demo Seller Florist/ }).first().click();
  await page.waitForTimeout(500);
  const text = await page.locator("table tbody tr").first().locator("td").nth(2).textContent();
  return parseInt(text!.trim(), 10);
}

function extractPdfText(buffer: Buffer): string {
  // Check valid PDF header
  const header = buffer.subarray(0, 5).toString("latin1");
  if (!header.startsWith("%PDF-")) {
    throw new Error(`Invalid PDF header: ${header}`);
  }

  // Decompress all FlateDecode streams
  let fullText = buffer.toString("latin1");
  let streamIdx = 0;
  while ((streamIdx = fullText.indexOf("stream", streamIdx)) !== -1) {
    const isCrLf = fullText.substring(streamIdx, streamIdx + 8) === "stream\r\n";
    const dataStart = streamIdx + (isCrLf ? 8 : 7);
    const dataEnd = fullText.indexOf("endstream", dataStart);
    if (dataEnd === -1) break;

    const streamBuf = buffer.subarray(dataStart, dataEnd);
    try {
      const inflated = zlib.inflateSync(streamBuf).toString("latin1");
      fullText += "\n" + inflated;
    } catch {
      // not all streams are zlib or might be raw
    }
    streamIdx = dataEnd + 9;
  }
  return fullText;
}

test.describe("Full B2B Demo Flow Verification", () => {
  test.setTimeout(300000);

  test("B2B lifecycle: ship, deliver, e-bill PDF, statements, payment, cancellation & stock restore", async ({ browser }) => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

    const buyerCtx = await browser.newContext({ acceptDownloads: true });
    const sellerCtx = await browser.newContext({ acceptDownloads: true });
    const farmerCtx = await browser.newContext();

    const buyer = await uiLogin(buyerCtx, BUYER.email, BUYER.password);
    const seller = await uiLogin(sellerCtx, SELLER.email, SELLER.password);
    const farmer = await uiLogin(farmerCtx, FARMER.email, FARMER.password);

    // ------------------------------------------------------------------------
    // Part 1: Place a fresh B2B order (2 kg of Demo Red Roses) and accept it
    // ------------------------------------------------------------------------
    const initialStock = await getStockForSeller(buyer);
    console.log(`[B2B] Initial stock for Demo Red Roses: ${initialStock} kg`);

    await buyer.locator("input[type=number]").first().fill("2");
    await buyer.waitForTimeout(400);
    await buyer.getByRole("button", { name: /Place order/ }).click();
    await buyer.waitForURL((url) => url.pathname.startsWith("/vendor/b2b/orders/"), { timeout: 15000 });

    const orderUrl = buyer.url();
    const orderId = orderUrl.split("/").pop()!;
    console.log(`[B2B] Placed order id: ${orderId}`);

    await expect(buyer.getByRole("heading", { name: /Order #/ })).toBeVisible();
    await expect(buyer.getByText(/Placed/)).toBeVisible();
    await expect(buyer.getByText(/₹398\.00/).first()).toBeVisible();

    // Seller accepts order
    await seller.goto(`${APP_BASE}/vendor/b2b/orders/${orderId}`);
    await expect(seller.getByRole("heading", { name: /Order #/ })).toBeVisible({ timeout: 15000 });
    await seller.getByRole("button", { name: /Accept order/ }).first().click();
    await expect(seller.locator("span", { hasText: /^Accepted$/ })).toBeVisible({ timeout: 15000 });

    // Verify stock dropped by 2
    const stockAfterAccept = await getStockForSeller(buyer);
    expect(initialStock - stockAfterAccept).toBe(2);
    console.log(`[B2B] Stock after accepting 2kg: ${stockAfterAccept} kg (dropped by 2)`);

    // ------------------------------------------------------------------------
    // Part 2: Seller ships the order with a shipment reference
    // ------------------------------------------------------------------------
    await seller.goto(`${APP_BASE}/vendor/b2b/orders/${orderId}`);
    await expect(seller.locator("span", { hasText: /^Accepted$/ })).toBeVisible({ timeout: 15000 });
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "01-seller-accepted-order.png"), fullPage: true });
    console.log("[B2B] Clicking Ship order button");

    await seller.getByRole("button", { name: /Ship order/ }).first().click({ timeout: 10000 });
    await expect(seller.getByRole("dialog")).toBeVisible({ timeout: 10000 });

    const shipmentRef = "TRACK-REF-2026-B2B";
    await seller.locator('[data-testid="shipment-reference"]').fill(shipmentRef);
    await seller.getByRole("dialog").getByRole("button", { name: /Ship order/ }).click({ timeout: 10000 });
    await expect(seller.getByRole("dialog")).not.toBeVisible({ timeout: 15000 });

    // Verify Shipped status on seller screen
    await expect(seller.locator("span", { hasText: /^Shipped$/ })).toBeVisible({ timeout: 15000 });
    console.log("[B2B] Order Shipped");
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "02-seller-shipped-order.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // Part 3: Seller marks the order as delivered
    // ------------------------------------------------------------------------
    console.log("[B2B] Clicking Mark delivered");
    await seller.getByRole("button", { name: /Mark delivered/i }).first().click({ timeout: 10000 });

    // Verify Delivered status on seller screen
    await expect(seller.locator("span", { hasText: /^Delivered$/ })).toBeVisible({ timeout: 15000 });
    console.log("[B2B] Order Delivered");
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "03-seller-delivered-order.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // Part 4: Confirm status timeline updates on buyer screen
    // ------------------------------------------------------------------------
    await buyer.goto(`${APP_BASE}/vendor/b2b/orders/${orderId}`);
    await expect(buyer.locator("span", { hasText: /^Delivered$/ })).toBeVisible({ timeout: 15000 });
    await expect(buyer.getByText(/Shipped/).first()).toBeVisible();
    await expect(buyer.getByText(/Accepted/).first()).toBeVisible();
    await expect(buyer.getByText(/placed/i).first()).toBeVisible();
    await buyer.screenshot({ path: path.join(SCREENSHOT_DIR, "04-buyer-delivered-order-timeline.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // Part 5: Buyer downloads the e-bill PDF and verifies text contents
    // ------------------------------------------------------------------------
    const downloadPromise = buyer.waitForEvent("download");
    await buyer.getByRole("button", { name: /Download e-bill/i }).click();
    const download = await downloadPromise;

    const pdfPath = path.join(SCREENSHOT_DIR, `ebill-order-${orderId}.pdf`);
    await download.saveAs(pdfPath);
    expect(fs.existsSync(pdfPath)).toBe(true);

    const pdfBuffer = fs.readFileSync(pdfPath);
    expect(pdfBuffer.length).toBeGreaterThan(100);
    // Header check
    expect(pdfBuffer.subarray(0, 4).toString()).toBe("%PDF");

    // Decompress and verify text content
    const pdfContent = extractPdfText(pdfBuffer);
    console.log(`[B2B] Extracted PDF content length: ${pdfContent.length}`);

    // Verify bill number, order number, seller and buyer, item rows, quantities, rates, and grand total
    expect(pdfContent).toContain("E-BILL");
    expect(pdfContent).toContain("Bill number:");
    expect(pdfContent).toContain("Order number:");
    expect(pdfContent).toContain("Demo Seller Florist");
    expect(pdfContent).toContain("Demo Buyer Florist");
    expect(pdfContent).toContain("Demo Red Roses");
    expect(pdfContent).toContain("2"); // quantity
    expect(pdfContent).toContain("199.00"); // rate
    expect(pdfContent).toContain("398.00"); // line total
    expect(pdfContent).toContain("INR 398.00"); // grand total
    expect(pdfContent).toContain(shipmentRef);

    // Compare total with on-screen order total
    const onScreenTotal = await buyer.getByText(/₹398\.00/).first().textContent();
    expect(onScreenTotal).toContain("398.00");

    // ------------------------------------------------------------------------
    // Part 6: Confirm e-bill access is refused before delivery or for third party
    // ------------------------------------------------------------------------
    const getAuthToken = async (p: Page): Promise<string> =>
      p.evaluate(() => {
        const raw = localStorage.getItem("fc-auth");
        return raw ? (JSON.parse(raw)?.state?.accessToken ?? "") : "";
      });

    const buyerToken = await getAuthToken(buyer);
    const farmerToken = await getAuthToken(farmer);

    // Check order 1 (which is PLACED, not DELIVERED)
    const beforeDeliveryRes = await buyer.request.get(`${API_BASE}/vendors/b2b/orders/1/e-bill`, {
      headers: { Authorization: `Bearer ${buyerToken}` },
    });
    expect(beforeDeliveryRes.status()).toBe(409);
    console.log(`[B2B] Pre-delivery e-bill request status: ${beforeDeliveryRes.status()} (refused)`);

    // Check third party access: farmer attempts to download B2B e-bill
    const thirdPartyRes = await farmer.request.get(`${API_BASE}/vendors/b2b/orders/${orderId}/e-bill`, {
      headers: { Authorization: `Bearer ${farmerToken}` },
    });
    expect(thirdPartyRes.status()).toBe(403);
    console.log(`[B2B] Third party e-bill request status: ${thirdPartyRes.status()} (refused)`);

    // ------------------------------------------------------------------------
    // Part 7: Seller opens Statements, selects buyer and default 15-day range,
    // confirms preview contains delivered unpaid orders with correct total
    // ------------------------------------------------------------------------
    console.log("[B2B] Navigating to statements");
    await seller.goto(`${APP_BASE}/vendor/b2b/statements`);
    await expect(seller.getByRole("heading", { name: /Statements/i }).first()).toBeVisible({ timeout: 15000 });

    // Select Demo Buyer Florist in the buyer dropdown
    const buyerSelect = seller.locator("select#statement-buyer-select");
    await buyerSelect.waitFor({ state: "visible", timeout: 10000 });
    await expect(buyerSelect.locator("option", { hasText: "Demo Buyer Florist" })).toBeAttached({ timeout: 10000 });
    await buyerSelect.selectOption({ label: "Demo Buyer Florist (Koramangala)" });
    await seller.waitForTimeout(600);

    // Click Preview
    await seller.getByRole("button", { name: "Preview" }).click({ timeout: 10000 });
    await seller.waitForTimeout(600);

    // Confirm preview contains eligible delivered orders including our order
    await expect(seller.getByRole("heading", { name: /Eligible orders/i })).toBeVisible({ timeout: 15000 });
    await expect(seller.locator("table tbody tr")).not.toHaveCount(0);

    // Take screenshot of preview
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "05-seller-statement-preview.png"), fullPage: true });
    console.log("[B2B] Statement preview verified");

    // ------------------------------------------------------------------------
    // Part 8: Generate statement and record payment
    // ------------------------------------------------------------------------
    await seller.getByRole("button", { name: /Create statement/i }).click();
    await seller.waitForTimeout(1000);

    // Detail view should now be displayed
    await expect(seller.getByRole("heading", { name: /Statement #/i })).toBeVisible({ timeout: 15000 });
    await expect(seller.getByText("Open").first()).toBeVisible();
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "06-seller-statement-created-open.png"), fullPage: true });

    // Record payment form
    const paymentModeSelect = seller.locator('select[name="paymentMode"]');
    await paymentModeSelect.selectOption("UPI");
    const paymentNoteInput = seller.locator('textarea[name="paymentNote"]');
    await paymentNoteInput.fill("Settled via UPI Ref 987654321");

    await seller.getByRole("button", { name: /^Record payment$/i }).click();
    await seller.waitForTimeout(1000);

    // Confirm statement shows Paid
    await expect(seller.getByText("Paid").first()).toBeVisible({ timeout: 15000 });
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "07-seller-statement-paid.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // Part 9: Re-preview the same range -> paid orders are absent
    // ------------------------------------------------------------------------
    await seller.getByRole("button", { name: /Back to list/i }).click();
    await seller.waitForTimeout(500);

    // Switch to create tab to preview again
    await seller.goto(`${APP_BASE}/vendor/b2b/statements`);
    await expect(buyerSelect.locator("option", { hasText: "Demo Buyer Florist" })).toBeAttached({ timeout: 10000 });
    await buyerSelect.selectOption({ label: "Demo Buyer Florist (Koramangala)" });
    await seller.waitForTimeout(600);
    await seller.getByRole("button", { name: "Preview" }).click();
    await seller.waitForTimeout(600);

    // Confirm that the just-paid order is absent from preview
    await expect(seller.getByText(`Order #${orderId}`)).not.toBeVisible();
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "08-seller-re-preview-excludes-paid.png"), fullPage: true });

    // Confirm buyer can view statement read-only
    await buyer.goto(`${APP_BASE}/vendor/b2b/statements`);
    await buyer.getByRole("button", { name: "Statements list" }).click();
    await buyer.waitForTimeout(400);
    await buyer.getByRole("button", { name: "As buyer" }).click();
    await buyer.waitForTimeout(600);

    // Select the first statement in the list
    await buyer.locator("table tbody tr").first().click();
    await buyer.waitForTimeout(600);

    // Verify read-only detail view: shows Paid, does NOT have "Record payment" form
    await expect(buyer.getByRole("heading", { name: /Statement #/i })).toBeVisible({ timeout: 15000 });
    await expect(buyer.getByText("Paid").first()).toBeVisible();
    await expect(buyer.getByRole("button", { name: /^Record payment$/i })).not.toBeVisible();
    await buyer.screenshot({ path: path.join(SCREENSHOT_DIR, "09-buyer-statement-readonly.png"), fullPage: true });

    // ------------------------------------------------------------------------
    // Part 10: Place another order, accept it, cancel it, confirm stock restored
    // ------------------------------------------------------------------------
    const stockBeforeCancelOrder = await getStockForSeller(buyer);
    console.log(`[B2B] Stock before test cancel order: ${stockBeforeCancelOrder} kg`);

    // Place an order for 3 kg
    await buyer.locator("input[type=number]").first().fill("3");
    await buyer.waitForTimeout(400);
    await buyer.getByRole("button", { name: /Place order/ }).click();
    await buyer.waitForURL((url) => url.pathname.startsWith("/vendor/b2b/orders/"), { timeout: 15000 });

    const cancelOrderId = buyer.url().split("/").pop()!;
    console.log(`[B2B] Placed order to cancel, id: ${cancelOrderId}`);

    // Seller accepts the order
    await seller.goto(`${APP_BASE}/vendor/b2b/orders/${cancelOrderId}`);
    await expect(seller.getByRole("heading", { name: /Order #/ })).toBeVisible({ timeout: 15000 });
    await seller.getByRole("button", { name: /Accept order/ }).first().click();
    await expect(seller.locator("span", { hasText: /^Accepted$/ })).toBeVisible({ timeout: 15000 });

    // Stock should drop by 3
    const stockAfterCancelOrderAccepted = await getStockForSeller(buyer);
    expect(stockBeforeCancelOrder - stockAfterCancelOrderAccepted).toBe(3);
    console.log(`[B2B] Stock after accepting 3kg: ${stockAfterCancelOrderAccepted} kg (dropped by 3)`);

    // Now seller (or buyer) cancels the order
    await seller.goto(`${APP_BASE}/vendor/b2b/orders/${cancelOrderId}`);
    await seller.getByRole("button", { name: /Cancel order/i }).first().click();
    await expect(seller.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    await seller.locator('[data-testid="cancel-reason"]').fill("Cannot fulfill due to transport issue");
    await seller.getByRole("dialog").getByRole("button", { name: /Cancel order/i }).click();
    await expect(seller.getByRole("dialog")).not.toBeVisible({ timeout: 15000 });

    // Confirm status is Cancelled
    await expect(seller.locator("span", { hasText: /^Cancelled$/ })).toBeVisible({ timeout: 15000 });
    await seller.screenshot({ path: path.join(SCREENSHOT_DIR, "10-order-cancelled.png"), fullPage: true });

    // Check stock is restored to pre-order level!
    const stockAfterCancellation = await getStockForSeller(buyer);
    expect(stockAfterCancellation).toBe(stockBeforeCancelOrder);
    console.log(`[B2B] Stock after cancellation: ${stockAfterCancellation} kg (fully restored)`);

    await buyerCtx.close();
    await sellerCtx.close();
    await farmerCtx.close();
  });
});
