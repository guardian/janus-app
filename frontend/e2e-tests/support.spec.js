import { expect, test } from "./fixtures.js";

test.use({ janusData: "support-current" });

test("shows dev-user in the current support slot with the test-only permission", async ({
  page,
}) => {
  await page.goto("/support");

  await expect(page).toHaveURL("/support");
  await expect(page).toHaveTitle("Support - Janus");
  await expect(
    page.getByRole("heading", { name: "Support", exact: true }),
  ).toBeVisible();
  const currentSupport = page.getByRole("main").locator(".card.horizontal");
  await expect(
    currentSupport.getByText("dev-user", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Very Important AWS Account", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("E2E test only - no AWS access")).toBeVisible();
  const credentialsLink = page.getByRole("link", {
    name: "vpn_key",
    exact: true,
  });
  await expect(credentialsLink).toBeVisible();
  await expect(credentialsLink).toHaveAttribute(
    "href",
    /\/credentials\?permissionId=very-important-aws-account-e2e-deny-all(?:&|$)/,
  );
});
