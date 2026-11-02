import { fileURLToPath } from "node:url";
import { defineConfig } from "@playwright/test";

const isCI = Boolean(process.env.CI);
const baseURL = "http://localhost:9102";

// eslint-disable-next-line import/no-default-export -- Playwright requires a default export.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  reporter: isCI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: 'sbt "Test / runMain E2ETestServer"',
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    env: { GOOGLE_AUTH_DISABLED: "true" },
    stdout: "pipe",
    url: `${baseURL}/healthcheck`,
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
