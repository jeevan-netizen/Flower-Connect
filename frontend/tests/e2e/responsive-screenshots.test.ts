import { test } from "@playwright/test";
import * as path from "path";

const SCREENSHOT_DIR = "tests/e2e/screenshots/responsive";

const VIEWPORTS = [
  { width: 360, height: 740, label: "mobile-small" },
  { width: 390, height: 740, label: "mobile" },
  { width: 768, height: 1024, label: "tablet" },
  { width: 1024, height: 768, label: "laptop" },
  { width: 1440, height: 900, label: "desktop" },
];

const SCREENSHOT_SCREENS = [
  { path: "/browse", label: "customer-home" },
  { path: "/search", label: "customer-search" },
  { path: "/vendor/orders", label: "vendor-order-board" },
  { path: "/vendor/catalog", label: "vendor-catalog" },
  { path: "/vendor/inventory", label: "vendor-inventory" },
  { path: "/vendor/billing", label: "vendor-billing" },
  { path: "/vendor/pos", label: "vendor-pos" },
  { path: "/admin/vendors", label: "admin-vendors" },
  { path: "/admin/billing", label: "admin-billing" },
];

test.describe("Responsive screenshots", () => {
  for (const viewport of VIEWPORTS) {
    test.describe(`viewport ${viewport.label}`, () => {
      test.use({ viewport: { width: viewport.width, height: viewport.height } });

      for (const screen of SCREENSHOT_SCREENS) {
        test(`capture ${screen.label}`, async ({ page }) => {
          await page.goto(screen.path);
          await page.waitForLoadState("networkidle");

          await page.screenshot({
            fullPage: true,
            path: path.join(SCREENSHOT_DIR, `${viewport.label}-${screen.label}.png`),
          });
        });
      }
    });
  }
});
