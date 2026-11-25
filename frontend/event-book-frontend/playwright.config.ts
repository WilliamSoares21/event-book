import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:4201',
    trace: 'on-first-retry',
    headless: true,
    timeout: 60000, // Aumentar o timeout para observação
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        headless: false, // Rodar em modo headed para depuração
      },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'], headless: false },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'], headless: false },
    },
  ],
  webServer: {
    command: 'npm run start -- --port 4201',
    url: 'http://localhost:4201',
    reuseExistingServer: !process.env.CI,
    timeout: 60000, // Aumentar timeout do webServer também
  },
});