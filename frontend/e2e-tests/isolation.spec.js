import { expect, test } from "./fixtures.js";

test.use({ janusData: "index-permission" });

test("keeps concurrent scenarios separate and leaves siblings running after teardown", async ({
  app,
  browser,
  createApp,
  page,
  playwright,
}) => {
  const other = await createApp("accounts-list");
  expect(other.baseURL).not.toBe(app.baseURL);
  const otherContext = await browser.newContext({ baseURL: other.baseURL });
  const manager = await playwright.request.newContext({
    baseURL: "http://127.0.0.1:9102",
  });
  try {
    const otherPage = await otherContext.newPage();
    await Promise.all([page.goto("/accounts"), otherPage.goto("/accounts")]);
    await expect(
      page.getByText("Test AWS Account", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("Mobile Apps AWS Account", { exact: true }),
    ).toHaveCount(0);
    await expect(
      otherPage.getByText("Mobile Apps AWS Account", { exact: true }),
    ).toBeVisible();
    await expect(
      otherPage.getByText("Test AWS Account", { exact: true }),
    ).toHaveCount(0);

    const deleted = await manager.delete(`/instances/${other.id}`);
    expect(deleted.status()).toBe(204);
    await expect
      .poll(async () => {
        try {
          await manager.get(`${other.baseURL}/healthcheck`, { timeout: 1_000 });
          return false;
        } catch {
          return true;
        }
      })
      .toBe(true);
    await page.reload();
    await expect(
      page.getByText("Test AWS Account", { exact: true }),
    ).toBeVisible();
    expect((await manager.get("/healthcheck")).ok()).toBe(true);

    const recreated = await createApp("accounts-list");
    expect(recreated.id).not.toBe(other.id);
    expect((await manager.get(`${recreated.baseURL}/accounts`)).ok()).toBe(
      true,
    );
  } finally {
    await otherContext.close();
    await manager.dispose();
  }
});

test("rejects an unknown scenario without affecting a running application", async ({
  page,
  playwright,
}) => {
  await page.goto("/accounts");
  const manager = await playwright.request.newContext({
    baseURL: "http://127.0.0.1:9102",
  });
  try {
    const response = await manager.post("/instances", {
      data: { janusData: "missing-scenario" },
    });
    expect(response.status()).toBe(400);
    expect((await response.json()).error).toContain("missing-scenario");
    await page.reload();
    await expect(
      page.getByText("Test AWS Account", { exact: true }),
    ).toBeVisible();
  } finally {
    await manager.dispose();
  }
});
