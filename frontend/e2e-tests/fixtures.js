import { test as base, expect } from "@playwright/test";

const managerURL = "http://127.0.0.1:9102";

export { expect };

export const test = base.extend({
  janusData: ["index-permission", { option: true }],

  createApp: [
    async ({ playwright }, use) => {
      const manager = await playwright.request.newContext({
        baseURL: managerURL,
        timeout: 90_000,
      });
      const apps = [];
      try {
        await use(async (janusData) => {
          const response = await manager.post("/instances", {
            data: { janusData },
          });
          expect(
            response.ok(),
            `Could not start JanusData fixture "${janusData}": ${await response.text()}`,
          ).toBe(true);
          const app = await response.json();
          apps.push(app);
          return app;
        });
      } finally {
        try {
          const results = await Promise.allSettled(
            apps.map(({ id }) => manager.delete(`/instances/${id}`)),
          );
          for (const [index, result] of results.entries()) {
            const message = `Could not stop E2E application ${apps[index].id}`;
            expect(result.status, `${message}: ${result.reason}`).toBe(
              "fulfilled",
            );
            const response = result.value;
            expect(
              response.status(),
              `${message}: ${await response.text()}`,
            ).toBe(204);
          }
        } finally {
          await manager.dispose();
        }
      }
    },
    { timeout: 120_000 },
  ],

  app: [
    async ({ createApp, janusData }, use) => {
      await use(await createApp(janusData));
    },
    { timeout: 120_000 },
  ],

  baseURL: async ({ app }, use) => {
    await use(app.baseURL);
  },
});
