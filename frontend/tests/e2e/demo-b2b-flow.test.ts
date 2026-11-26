import { expect, test, type BrowserContext, type Page } from "@playwright/test";

const APP_BASE = "http://localhost:5173";
const SELLER = { email: "seller.demo@flowerconnect.test", password: "SellerDemo123!" };
const BUYER = { email: "buyer.demo@flowerconnect.test", password: "BuyerDemo123!" };

async function uiLogin(ctx: BrowserContext, email: string, password: string): Promise<Page> {
  const page = await ctx.newPage();
  await page.goto(`${APP_BASE}/login`);
  await page.waitForLoadState("domcontentloaded");
  await page.fill('input#email', email);
  await page.fill('input#password', password);
  await page.click('button:has-text("Sign in")');
  await page.waitForURL(url => !url.pathname.startsWith("/login"), { timeout: 15000 });
  return page;
}

async function stockForSeller(page: Page): Promise<number> {
  await page.goto(`${APP_BASE}/vendor/b2b/place-order`);
  await page.getByPlaceholder(/Search sellers/).fill("Demo Seller");
  await page.waitForTimeout(400);
  await page.getByRole("button", { name: /Demo Seller Florist/ }).first().click();
  await page.waitForTimeout(400);
  const text = await page.locator("table tbody tr").first().locator("td").nth(2).textContent();
  return parseInt(text!, 10);
}

test("demo: buyer places B2B order, seller accepts, stock drops by ordered quantity", async ({ browser }) => {
  test.setTimeout(240000);
  const buyerCtx = await browser.newContext();
  const sellerCtx = await browser.newContext();
  const buyer = await uiLogin(buyerCtx, BUYER.email, BUYER.password);
  const seller = await uiLogin(sellerCtx, SELLER.email, SELLER.password);

  const stockBefore = await stockForSeller(buyer);

  await buyer.locator("input[type=number]").first().fill("4");
  await buyer.waitForTimeout(400);
  await buyer.getByRole("button", { name: /Place order/ }).click();
  await buyer.waitForURL(url => url.pathname.startsWith("/vendor/b2b/orders/"));
  await expect(buyer.getByRole("heading", { name: /Order #/ })).toBeVisible();
  await expect(buyer.getByText(/Placed/)).toBeVisible();
  await expect(buyer.getByText(/₹796\.00/).first()).toBeVisible();
  const orderId = buyer.url().split("/").pop()!;

  await seller.goto(`${APP_BASE}/vendor/b2b/orders/${orderId}`);
  await expect(seller.getByRole("heading", { name: /Order #/ })).toBeVisible({ timeout: 15000 });
  await seller.getByRole("button", { name: /Accept order/ }).first().click();
  await seller.waitForTimeout(600);
  await expect(seller.getByText(/Accepted/)).toBeVisible({ timeout: 15000 });

  const stockAfter = await stockForSeller(buyer);
  expect(stockBefore - stockAfter).toBe(4);

  await buyer.close();
  await seller.close();
});
