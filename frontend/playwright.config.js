import { fileURLToPath } from "node:url";
import { defineConfig } from "@playwright/test";

const serverURL = "http://localhost:9102";
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? serverURL;

// eslint-disable-next-line import/no-default-export -- Playwright requires a default export.
export default defineConfig({
  testDir: "./e2e",
  forbidOnly: !!process.env.CI,
  use: {
    baseURL,
    browserName: "chromium",
    trace: "retain-on-failure",
  },
  webServer: {
    command: 'sbt "Test / runMain BrowserTestServer"',
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    env: { GOOGLE_AUTH_DISABLED: "true" },
    stdout: "pipe",
    url: `${serverURL}/healthcheck`,
    reuseExistingServer: false,
    gracefulShutdown: { signal: "SIGTERM", timeout: 10_000 },
    timeout: 300_000,
  },
});
