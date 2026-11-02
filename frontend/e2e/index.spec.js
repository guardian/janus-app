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
