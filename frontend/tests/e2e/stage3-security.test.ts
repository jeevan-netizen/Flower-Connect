import { randomBytes, randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext } from "@playwright/test";

const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:5173";

async function api(
  request: APIRequestContext,
  method: "get" | "post",
  path: string,
  data?: unknown,
  headers?: Record<string, string>,
) {
  const url = `${API_BASE}${path}`;
  if (method === "get") return request.get(url, { headers });
  return request.post(url, { data, headers });
}

async function expectStatus(response: { status(): number; text(): Promise<string> }, expected: number) {
  const body = await response.text().catch(() => "");
  expect(response.status(), body).toBe(expected);
}

test("Stage 3: customer login, session restore, logout, CSP checks", async ({ page, context }) => {
  test.setTimeout(120_000);

  const request = context.request;
  const accountPassword = `${randomBytes(32).toString("base64url")}Aa1!`;
  const suffix = randomUUID().slice(0, 8);
  const customerEmail = `e2e-stage3-${suffix}@flowerconnect.test`;

  // Register a customer via API
  const regResponse = await api(request, "post", "/auth/register", {
    email: customerEmail,
    password: accountPassword,
    fullName: "Stage 3 E2E Customer",
    phone: `+91${Date.now().toString().slice(-10)}`,
  });
  await expectStatus(regResponse, 200);

  // Collect console messages for CSP violations
  const consoleMessages: string[] = [];
  page.on("console", (msg) => {
    const text = msg.text();
    if (text.includes("Content-Security-Policy") || text.includes("CSP") || msg.type() === "error") {
      consoleMessages.push(`${msg.type()}: ${text}`);
    }
  });

  // --- Login ---
  await page.goto(`${BASE_URL}/login`);
  await page.getByLabel("Email address").fill(customerEmail);
  await page.getByLabel("Password").fill(accountPassword);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/$/);

  // --- No token in web storage ---
  const storageAfterLogin = await page.evaluate(() => ({
    localStorage: Object.keys(localStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
    sessionStorage: Object.keys(sessionStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
  }));
  expect(storageAfterLogin.localStorage).toEqual([]);
  expect(storageAfterLogin.sessionStorage).toEqual([]);

  // --- Page reload restores session ---
  await page.reload();
  await page.waitForLoadState("networkidle");
  // Session should be restored via refresh cookie + refresh flow
  const isAuthenticatedAfterReload = await page.evaluate(() => {
    // Check if the app considers itself authenticated (no login redirect)
    return window.location.pathname === "/" || window.location.pathname === "/browse";
  });
  expect(isAuthenticatedAfterReload).toBe(true);

  // --- No token in web storage after reload ---
  const storageAfterReload = await page.evaluate(() => ({
    localStorage: Object.keys(localStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
    sessionStorage: Object.keys(sessionStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
  }));
  expect(storageAfterReload.localStorage).toEqual([]);
  expect(storageAfterReload.sessionStorage).toEqual([]);

  // --- No CSP violations ---
  const cspViolations = consoleMessages.filter((m) => m.includes("Content-Security-Policy"));
  expect(cspViolations).toEqual([]);

  // --- Logout ---
  // Navigate to a page that has a logout link, or call logout API then check UI
  await page.evaluate(() => {
    // Dispatch a custom event or call logout via the app
    window.dispatchEvent(new CustomEvent("test-logout"));
  });
  // Use the API to logout (with CSRF headers required by cookie-auth endpoints),
  // then verify the UI reflects it
  const logoutResponse = await api(request, "post", "/auth/logout", undefined, {
    "X-FlowerConnect-Client": "1",
    Origin: BASE_URL,
  });
  await expectStatus(logoutResponse, 204);

  // Navigate to a protected page - should redirect to login
  await page.goto(`${BASE_URL}/orders`);
  await page.waitForLoadState("networkidle");
  await expect(page).toHaveURL(/\/login/);

  // --- No token in web storage after logout ---
  const storageAfterLogout = await page.evaluate(() => ({
    localStorage: Object.keys(localStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
    sessionStorage: Object.keys(sessionStorage).filter(
      (k) => k === "fc-auth" || k.includes("accessToken") || k.includes("refreshToken") || k.includes("token"),
    ),
  }));
  expect(storageAfterLogout.localStorage).toEqual([]);
  expect(storageAfterLogout.sessionStorage).toEqual([]);

  // --- No CSP violations overall ---
  const allCspViolations = consoleMessages.filter((m) => m.includes("Content-Security-Policy"));
  expect(allCspViolations).toEqual([]);
});
