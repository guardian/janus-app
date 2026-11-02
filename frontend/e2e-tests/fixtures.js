import { test as base, expect } from "@playwright/test";

const managerURL = "http://127.0.0.1:9102";

export { expect };

export const test = base.extend({
  janusData: ["index-permission", { option: true }],

  app: [
    async ({ janusData, playwright }, use) => {
      const manager = await playwright.request.newContext({
        baseURL: managerURL,
        timeout: 90_000,
      });
      try {
        const response = await manager.post("/instances", {
          data: { janusData },
        });
        expect(
          response.ok(),
          `Could not start JanusData fixture "${janusData}": ${await response.text()}`,
        ).toBe(true);
        const app = await response.json();
        try {
          await use(app);
        } finally {
          const response = await manager.delete(`/instances/${app.id}`);
          expect(
            response.status(),
            `Could not stop E2E application ${app.id}: ${await response.text()}`,
          ).toBe(204);
        }
      } finally {
        await manager.dispose();
      }
    },
    { timeout: 120_000 },
  ],

  baseURL: async ({ app }, use) => {
    await use(app.baseURL);
  },
});
