import { expect, test } from "@playwright/test";

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
});
