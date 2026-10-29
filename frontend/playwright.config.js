import { fileURLToPath } from "node:url";
import { defineConfig } from "@playwright/test";

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:9100";

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
    command: process.env.CI
      ? 'sbt "Test / runMain BrowserTestServer"'
      : 'sbt -Dauth.google.disabled=true "run 9100"',
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    env: { GOOGLE_AUTH_DISABLED: "true" },
    stdout: "pipe",
    url: "http://localhost:9100/healthcheck",
    reuseExistingServer: false,
    gracefulShutdown: { signal: "SIGTERM", timeout: 10_000 },
    timeout: 300_000,
  },
});
