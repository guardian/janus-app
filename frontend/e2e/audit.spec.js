import { expect, test } from "@playwright/test";

const accountPath = "/audit/account/test-aws-account";
const populatedAccountURL = `${accountPath}?date=2020-05-12`;
const accountRows = [
  [
    "e2e-other-user",
    "e2e-deny-all",
    "Console cloud",
    "2020/05/12 12:00:00",
    "15 minutes",
    "Account Permission",
    "-",
  ],
  [
    "dev-user",
    "e2e-deny-all",
    "Credentials vpn_key",
    "2020/05/12 11:00:00",
    "30 minutes",
    "Account Permission",
    "done",
  ],
  [
    "dev-user",
    "e2e-deny-all",
    "Console cloud",
    "2020/05/12 10:00:00",
    "1 hour",
    "Account Permission",
    "-",
  ],
];

async function expectRows(page, expectedRows) {
  const rows = page.getByRole("table").locator("tbody tr");
  await expect(rows).toHaveCount(expectedRows.length);
  for (const [index, cells] of expectedRows.entries()) {
    await expect(rows.nth(index).getByRole("cell")).toHaveText(cells);
  }
}

test("opens account audit history from the index card", async ({ page }) => {
  await page.goto("/");
  const accountCard = page.locator('[data-account-key="test-aws-account"]');
  const [response] = await Promise.all([
    page.waitForResponse(
      (response) => new URL(response.url()).pathname === accountPath,
    ),
    accountCard.getByRole("link", { name: "View audit trail" }).click(),
  ]);

  expect(response.status()).toBe(200);
  await expect(page).toHaveURL(accountPath);
  await expect(page).toHaveTitle("Audit trail - Janus");
  await expect(
    page.getByRole("heading", { name: "Audit trail", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText(/For\s+test-aws-account,\s+week commencing Monday/),
  ).toBeVisible();
});

test("renders account audit entries newest first", async ({ page }) => {
  const response = await page.goto(populatedAccountURL);
  expect(response.status()).toBe(200);
  await expect(
    page.getByText(
      "For test-aws-account, week commencing Monday 11 May, 2020.",
      { exact: true },
    ),
  ).toBeVisible();
  await expect(page.getByRole("columnheader")).toHaveText([
    "User",
    "Access",
    "Access Type",
    "Time (UTC)",
    "Duration",
    "Permission Type",
    "External error",
  ]);
  await expectRows(page, accountRows);
  await expect(
    page
      .getByRole("row")
      .filter({ hasText: "2020/05/12 11:00:00" })
      .getByRole("link", { name: "dev-user", exact: true }),
  ).toHaveAttribute("href", "/audit/user/dev-user");
});

test("shows the empty state for an unseeded week", async ({ page }) => {
  const response = await page.goto(`${accountPath}?date=2020-05-05`);
  expect(response.status()).toBe(200);
  await expectRows(page, [
    ["No records found for the week commencing 4 May, 2020"],
  ]);
});

test("follows a username and renders only that user's audit entries", async ({
  page,
}) => {
  await page.goto(populatedAccountURL);
  const credentialsRow = page
    .getByRole("row")
    .filter({ hasText: "2020/05/12 11:00:00" });
  await credentialsRow
    .getByRole("link", { name: "dev-user", exact: true })
    .click();
  await expect(page).toHaveURL("/audit/user/dev-user");
  await expect(page).toHaveTitle("Audit trail - Janus");
  await expect(
    page.getByText(/For\s+dev-user,\s+week commencing Monday/),
  ).toBeVisible();

  // Audit links use the current week; select the fixture week explicitly.
  const response = await page.goto("/audit/user/dev-user?date=2020-05-12");
  expect(response.status()).toBe(200);
  await expect(page.getByRole("columnheader").first()).toHaveText("Account");
  await expectRows(
    page,
    accountRows
      .filter(([username]) => username === "dev-user")
      .map(([, ...cells]) => ["test-aws-account", ...cells]),
  );
  const accountLinks = page
    .getByRole("table")
    .getByRole("link", { name: "test-aws-account", exact: true });
  await expect(accountLinks).toHaveCount(2);
  for (const link of await accountLinks.all()) {
    await expect(link).toHaveAttribute("href", accountPath);
  }
});
