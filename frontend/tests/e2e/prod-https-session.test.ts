import { test, expect } from "@playwright/test";

test.use({ ignoreHTTPSErrors: true });

test("Prod-like HTTPS session flow verifies Secure cookie storage and refresh", async ({ page, context }) => {
  // 1. Visit https://localhost:8443/
  await page.goto("https://localhost:8443/");
  await expect(page).toHaveTitle(/FlowerConnect/i);

  // 2. Perform login via fetch inside the browser page so Chromium network stack stores the Secure cookie
  const adminPassword = process.env.ADMIN_PASSWORD || "AdminPass123!";
  const loginResult = await page.evaluate(async (pwd) => {
    const res = await fetch("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: "admin@flowerconnect.test",
        password: pwd,
      }),
    });
    const data = await res.json();
    return { status: res.status, data };
  }, adminPassword);

  expect(loginResult.status).toBe(200);
  expect(loginResult.data.accessToken).toBeTruthy();

  // 3. Inspect cookies in browser context matching the cookie path /api/v1/auth
  const cookies = await context.cookies("https://localhost:8443/api/v1/auth");
  const refreshCookie = cookies.find((c) => c.name === "refresh_token");
  expect(refreshCookie).toBeDefined();
  expect(refreshCookie?.secure).toBe(true);
  expect(refreshCookie?.httpOnly).toBe(true);
  expect(refreshCookie?.sameSite).toBe("Strict");
  expect(refreshCookie?.path).toBe("/api/v1/auth");

  // 4. Exercise session refresh endpoint from inside the browser:
  // The browser automatically attaches the HttpOnly Secure refresh_token cookie
  const refreshResult = await page.evaluate(async () => {
    const res = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: {
        "X-FlowerConnect-Client": "1",
      },
    });
    const data = await res.json();
    return { status: res.status, data };
  });

  expect(refreshResult.status).toBe(200);
  expect(refreshResult.data.accessToken).toBeTruthy();
});
