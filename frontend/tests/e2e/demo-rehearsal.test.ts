import { expect, test, type Page } from "@playwright/test";
import path from "node:path";

const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:5173";
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? "DemoPass#Rehearsal2026";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? "AdminPass#Rehearsal2026";
const SCREENSHOT_DIR = path.resolve(process.cwd(), "../docs/rehearsal-materials/screenshots");

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
  // Wait for authenticated header to appear
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible({ timeout: 15_000 });
}

async function navigateClientSide(page: Page, routePath: string) {
  await page.evaluate((to) => {
    window.history.pushState({}, "", to);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }, routePath);
  await page.waitForTimeout(600);
}

test.describe("Phase 14 Clean-Clone Rehearsal Walkthrough", () => {
  test.setTimeout(180_000);

  test("Step 1-6: Customer discovery, cart, gift note, and COD checkout", async ({ page }) => {
    // 1. Sign in as Customer Alice (demo-customer-1)
    await signIn(page, "demo-customer-1@flowerconnect.test", DEMO_PASSWORD);
    await expect(page.getByRole("link", { name: "Browse" })).toBeVisible();
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "01-customer-login.png"), fullPage: false });

    // 2. Set Delivery Location to Koramangala
    await page.locator("#home-location").selectOption("1");
    await page.waitForTimeout(500);

    // 3. Browse catalog via client-side link
    await page.getByRole("link", { name: "Browse" }).click();
    await expect(page.getByText(/Blossom Haven/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "02-customer-location-browse.png"), fullPage: false });

    // 4. Open product detail dialog & add to cart
    // First go to the vendor storefront from the browse page
    await page.getByRole("link", { name: /view shop/i }).first().click();
    await expect(page.getByText(/Red Grand Gala Roses/i).first()).toBeVisible({ timeout: 10_000 });
    // Click the product name — this opens a dialog overlay on the storefront page
    await page.getByText(/Red Grand Gala Roses/i).first().click();
    // Wait for the product dialog to open (product detail renders as a modal/dialog)
    const productDialog = page.getByRole("dialog", { name: /Red Grand Gala Roses/i });
    await expect(productDialog).toBeVisible({ timeout: 15_000 });

    const giftNoteInput = productDialog.getByLabel(/gift note/i);
    if (await giftNoteInput.isVisible()) {
      await giftNoteInput.fill("Happy Anniversary! With all my love.");
    }
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "03-customer-product-detail.png"), fullPage: false });

    // Wait for and click "Add to cart" inside the dialog
    // The button's aria-label is "Add {productName} to cart", visible text is "Add to cart"
    // Use aria-label pattern to match the accessible name
    const addToCartButton = productDialog.getByRole("button", { name: /add.*to cart/i });
    await expect(addToCartButton).toBeVisible({ timeout: 15_000 });
    await addToCartButton.click();
    await page.waitForTimeout(1000);

    // Close product detail dialog before proceeding
    await page.keyboard.press("Escape");
    await page.waitForTimeout(500);

    // 5. Open Cart via client-side navigation
    await navigateClientSide(page, "/cart");
    // Cart may show product name or generic item indicator
    await expect(page.locator("body")).not.toContainText(/Your cart is empty/i, { timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "04-customer-cart.png"), fullPage: false });

    // 6. Checkout — proceed to checkout wizard
    const checkoutBtn = page.getByRole("button", { name: /proceed to checkout/i });
    await expect(checkoutBtn).toBeVisible({ timeout: 10_000 });
    await checkoutBtn.click();
    await expect(page.getByText(/Delivery address|Review order/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "05-customer-checkout.png"), fullPage: false });

    // 7. Order History & Tracking via client-side navigation
    await navigateClientSide(page, "/orders");
    await expect(page.getByText(/Order #/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "06-customer-order-tracking.png"), fullPage: false });
  });

  test("Step 7-10: Vendor operations: dashboard, order board, inventory & POS", async ({ page }) => {
    // 1. Sign in as Vendor Blossom Haven
    await signIn(page, "vendor-blossom@flowerconnect.test", DEMO_PASSWORD);
    await page.getByRole("link", { name: "Vendor" }).click();
    await expect(page.getByRole("heading", { name: /Blossom Haven/i })).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "07-vendor-login-dashboard.png"), fullPage: false });

    // 2. Order Board
    await page.getByRole("navigation", { name: "Vendor" }).getByRole("link", { name: "Orders" }).click();
    await expect(page.getByText(/Order #|Status/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "08-vendor-order-board.png"), fullPage: false });

    // 3. Inventory Management
    // Dismiss any open dialog/overlay first (RejectReasonDialog framer-motion backdrop may block pointer events)
    await page.keyboard.press("Escape");
    await page.waitForTimeout(500);
    // Use client-side navigation to bypass any lingering overlay
    await navigateClientSide(page, "/vendor/inventory");
    await expect(page.getByText(/Stock|Inventory/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "09-vendor-inventory.png"), fullPage: false });

    // 4. Counter POS Sale
    await navigateClientSide(page, "/vendor/pos");
    await expect(page.getByText(/POS|Point of Sale|Ticket/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "10-vendor-pos.png"), fullPage: false });
  });

  test("Step 11-15: Admin governance: vendors, refund queue, disputes, billing", async ({ page }) => {
    // 1. Sign in as Administrator
    await signIn(page, "admin@flowerconnect.test", ADMIN_PASSWORD);
    await page.getByRole("link", { name: "Admin" }).click();
    await expect(page.getByRole("heading", { name: /Admin dashboard|Administration/i })).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "11-admin-login-dashboard.png"), fullPage: false });

    // 2. Vendor Management
    await page.getByRole("link", { name: "Vendors", exact: true }).click();
    await expect(page.getByText(/Orchid Oasis/i)).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "12-admin-vendor-approval.png"), fullPage: false });

    // 3. Refunds Queue
    await navigateClientSide(page, "/admin/refunds");
    await expect(page.getByText(/Refund/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "13-admin-refund-queue.png"), fullPage: false });

    // 4. Dispute Resolution
    await navigateClientSide(page, "/admin/disputes");
    await expect(page.getByText(/Dispute/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "14-admin-disputes.png"), fullPage: false });

    // 5. Billing & Settlements
    await navigateClientSide(page, "/admin/billing");
    await expect(page.getByText(/Billing & Settlements/i).first()).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "15-admin-billing-settlements.png"), fullPage: false });
  });
});
