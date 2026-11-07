import { test, expect } from "@playwright/test";

const VIEWPORTS = [
  { width: 360, height: 740, label: "mobile-small" },
  { width: 390, height: 740, label: "mobile" },
  { width: 768, height: 1024, label: "tablet" },
  { width: 1024, height: 768, label: "laptop" },
  { width: 1440, height: 900, label: "desktop" },
];

// Key screens to check for responsive and accessibility issues
const SCREENS = [
  { path: "/browse", label: "customer-home", needsAuth: false },
  { path: "/search", label: "customer-search", needsAuth: false },
  { path: "/cart", label: "customer-cart", needsAuth: true },
  { path: "/checkout", label: "customer-checkout", needsAuth: true },
  { path: "/orders", label: "customer-orders", needsAuth: true },
  { path: "/vendor/orders", label: "vendor-order-board", needsAuth: true },
  { path: "/vendor/catalog", label: "vendor-catalog", needsAuth: true },
  { path: "/vendor/inventory", label: "vendor-inventory", needsAuth: true },
  { path: "/vendor/billing", label: "vendor-billing", needsAuth: true },
  { path: "/vendor/pos", label: "vendor-pos", needsAuth: true },
  { path: "/admin/vendors", label: "admin-vendors", needsAuth: true },
  { path: "/admin/billing", label: "admin-billing", needsAuth: true },
];

test.describe("Responsive layout and accessibility", () => {
  for (const viewport of VIEWPORTS) {
    test.describe(`viewport ${viewport.width}x${viewport.height} (${viewport.label})`, () => {
      test.use({ viewport: { width: viewport.width, height: viewport.height } });

      for (const screen of SCREENS) {
        test(`${screen.label} renders without horizontal overflow`, async ({ page }) => {
          await page.goto(screen.path);
          await page.waitForLoadState("networkidle");

          const overflow = await page.evaluate(() => {
            const scrollWidth = document.documentElement.scrollWidth;
            const clientWidth = document.documentElement.clientWidth;
            return scrollWidth - clientWidth;
          });

          expect(overflow).toBeLessThanOrEqual(2);
        });

        test(`${screen.label} buttons have accessible names at ${viewport.label}`, async ({ page }) => {
          await page.goto(screen.path);
          await page.waitForLoadState("networkidle");

          const buttons = page.locator("button");
          const count = await buttons.count();

          for (let i = 0; i < count; i++) {
            const button = buttons.nth(i);
            const ariaLabel = await button.getAttribute("aria-label");
            const textContent = await button.textContent();
            const hasAccessibleName = (ariaLabel && ariaLabel.trim().length > 0) || (textContent && textContent.trim().length > 0);

            expect(hasAccessibleName).toBe(true);
          }
        });

        test(`${screen.label} links have accessible names at ${viewport.label}`, async ({ page }) => {
          await page.goto(screen.path);
          await page.waitForLoadState("networkidle");

          const links = page.locator("a[href]");
          const count = await links.count();

          for (let i = 0; i < count; i++) {
            const link = links.nth(i);
            const ariaLabel = await link.getAttribute("aria-label");
            const textContent = await link.textContent();
            const hasAccessibleName = (ariaLabel && ariaLabel.trim().length > 0) || (textContent && textContent.trim().length > 0);

            expect(hasAccessibleName).toBe(true);
          }
        });

        test(`${screen.label} focused elements have visible focus indicators at ${viewport.label}`, async ({ page }) => {
          await page.goto(screen.path);
          await page.waitForLoadState("networkidle");

          await page.keyboard.press("Tab");
          await page.waitForTimeout(100);

          const focused = page.locator(":focus-visible");
          const count = await focused.count();

          if (count > 0) {
            const focusStyle = await focused.first().evaluate((el) => {
              const style = window.getComputedStyle(el);
              const outline = style.outline;
              const boxShadow = style.boxShadow;
              return outline !== "none" || boxShadow !== "none";
            });

            expect(focusStyle).toBe(true);
          }
        });
      }
    });
  }
});
