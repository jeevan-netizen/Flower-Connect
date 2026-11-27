import { expect, test } from "@playwright/test";
import * as path from "path";

const APP_BASE = "http://localhost:5173";
const SCREENSHOT_DIR = "C:\\Users\\jeeva\\AppData\\Local\\Temp\\flowerconnect-demo-screenshots\\logins";

const ACCOUNTS = [
  {
    role: "ADMIN",
    email: "admin@flowerconnect.local",
    password: "flowerconnect123",
    roleLink: "Admin",
    portalUrl: "/admin",
    portalHeading: /Admin/i,
  },
  {
    role: "FARMER",
    email: "farmer.demo@flowerconnect.test",
    password: "FarmerDemo123!",
    roleLink: "Farmer",
    portalUrl: "/farmer",
    portalHeading: /My florists/i,
  },
  {
    role: "FLORIST (Seller)",
    email: "seller.demo@flowerconnect.test",
    password: "SellerDemo123!",
    roleLink: "Vendor",
    portalUrl: "/vendor",
    portalHeading: /Vendor|Florist/i,
  },
  {
    role: "FLORIST (Buyer)",
    email: "buyer.demo@flowerconnect.test",
    password: "BuyerDemo123!",
    roleLink: "Vendor",
    portalUrl: "/vendor",
    portalHeading: /Vendor|Florist/i,
  },
];

test.describe("Seeded account logins", () => {
  for (const acc of ACCOUNTS) {
    test(`login as ${acc.role} (${acc.email})`, async ({ browser }) => {
      const context = await browser.newContext();
      const page = await context.newPage();

      await page.goto(`${APP_BASE}/login`);
      await page.waitForLoadState("domcontentloaded");

      await page.fill("input#email", acc.email);
      await page.fill("input#password", acc.password);
      await page.click('button:has-text("Sign in")');

      // Wait for navigation away from /login
      await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });

      // Verify on-screen presence of Log out button
      const logoutBtn = page.getByRole("button", { name: "Log out" });
      await expect(logoutBtn).toBeVisible({ timeout: 10000 });

      // Verify the role link is present in header
      const roleLink = page.getByRole("link", { name: acc.roleLink, exact: true });
      await expect(roleLink).toBeVisible({ timeout: 10000 });

      // Navigate to portal
      await roleLink.click();
      await page.waitForURL((url) => url.pathname.startsWith(acc.portalUrl), { timeout: 15000 });
      await expect(page.getByRole("heading", { name: acc.portalHeading }).first()).toBeVisible({ timeout: 10000 });

      // Take screenshot of portal
      const safeRole = acc.role.toLowerCase().replace(/[^a-z0-9]/g, "-");
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, `${safeRole}-portal.png`), fullPage: true });

      await context.close();
    });
  }
});
