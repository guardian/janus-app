import { expect, test } from "@playwright/test";

test("shows the index page for a user without permissions", async ({
  page,
}) => {
  const [stylesheet, script] = await Promise.all([
    page.waitForResponse(/\/[^/]*main\.css$/),
    page.waitForResponse(/\/[^/]*janus\.js$/),
    page.goto("/"),
  ]);

  expect(stylesheet.ok()).toBe(true);
  expect(script.ok()).toBe(true);
  await expect(page).toHaveURL("/");
  await expect(page).toHaveTitle("Home - Janus");
  await expect(
    page.getByRole("heading", { name: "No permissions", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText(
      "You do not have any permissions. If this doesn't seem right, you can check your permissions in Janus' configuration.",
    ),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Dev User", exact: true }),
  ).toBeVisible();
  const logoutLink = page.getByRole("link", { name: "Not you? Logout here." });
  await expect(logoutLink).toBeVisible();
  await expect(logoutLink).toHaveAttribute("href", "/logout");
});
