import { expect, test } from "@playwright/test";

test("shows the test-only AWS account permission", async ({ page }) => {
  const [stylesheet, script] = await Promise.all([
    page.waitForResponse(/\/[^/]*main\.css$/),
    page.waitForResponse(/\/[^/]*janus\.js$/),
    page.goto("/"),
  ]);

  expect(stylesheet.ok()).toBe(true);
  expect(script.ok()).toBe(true);
  await expect(page).toHaveURL("/");
  await expect(page).toHaveTitle("Your permissions - Janus");
  await expect(
    page.getByText("Test AWS Account", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("E2E test only - no AWS access")).toBeVisible();
  const credentialsLink = page.getByRole("link", {
    name: "vpn_key",
    exact: true,
  });
  await expect(credentialsLink).toBeVisible();
  await expect(credentialsLink).toHaveAttribute(
    "href",
    /\/credentials\?permissionId=test-aws-account-e2e-deny-all(?:&|$)/,
  );
  await expect(
    page.getByRole("link", { name: "Dev User", exact: true }),
  ).toBeVisible();
});

for (const { access, icon, path } of [
  { access: "console", icon: "cloud", path: "/console" },
  { access: "credentials", icon: "vpn_key", path: "/credentials" },
]) {
  test(`clicking ${access} access requests the test permission`, async ({
    page,
    context,
  }) => {
    await page.goto("/");
    const origin = new URL(page.url()).origin;
    const timezoneOffset = await page.evaluate(() =>
      String(-new Date().getTimezoneOffset() / 60),
    );

    // Block all subsequent navigation, including an incorrectly wired link,
    // so no federation request reaches the backend.
    await context.route("**/*", (route) =>
      route.request().isNavigationRequest()
        ? route.fulfill({
            status: 200,
            contentType: "text/plain",
            body: "E2E intercepted navigation; no AWS request was made.",
          })
        : route.continue(),
    );

    const [request] = await Promise.all([
      page.waitForRequest((request) => request.isNavigationRequest()),
      page.getByRole("link", { name: icon, exact: true }).click(),
    ]);

    const url = new URL(request.url());
    expect(request.method()).toBe("GET");
    expect(url.origin).toBe(origin);
    expect(url.pathname).toBe(path);
    expect(url.searchParams.get("permissionId")).toBe(
      "test-aws-account-e2e-deny-all",
    );
    expect(url.searchParams.get("tzOffset")).toBe(timezoneOffset);
    await expect(page).toHaveURL(url.href);
  });
}
