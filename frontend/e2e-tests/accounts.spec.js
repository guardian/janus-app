import { expect, test } from "./fixtures.js";

test.use({ janusData: "accounts-list" });

test("shows the accounts page", async ({ page }) => {
  const [stylesheet, script] = await Promise.all([
    page.waitForResponse(/\/[^/]*main\.css$/),
    page.waitForResponse(/\/[^/]*janus\.js$/),
    page.goto("/accounts"),
  ]);

  expect(stylesheet.ok()).toBe(true);
  expect(script.ok()).toBe(true);
  await expect(page).toHaveURL("/accounts");
  await expect(page).toHaveTitle("Accounts - Janus");
  await expect(
    page.getByRole("heading", { name: "Accounts", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Dev User", exact: true }),
  ).toBeVisible();
  const testAccount = page
    .getByRole("row")
    .filter({ hasText: "Mobile Apps AWS Account" });
  await expect(testAccount).toContainText("000000000002");
  await expect(
    testAccount.getByRole("link", { name: "1", exact: true }),
  ).toHaveAttribute("href", "/users/account/test-aws-account-2");
});
