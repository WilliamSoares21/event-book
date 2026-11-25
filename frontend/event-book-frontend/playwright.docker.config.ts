import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    headless: true,
    timeout: 60000,
  },
  projects: [
    {
      name: 'chromium-docker',
      use: {
        ...devices['Desktop Chrome'],
        headless: false,
      },
    },
    {
      name: 'firefox-docker',
      use: { ...devices['Desktop Firefox'], headless: false },
    },
    {
      name: 'webkit-docker',
      use: { ...devices['Desktop Safari'], headless: false },
    },
  ],
  // No webServer - Docker Compose manages the services
  // Tests will fail fast if services are not available
  globalSetup: require.resolve('./e2e/global-setup-docker.ts'),
  globalTeardown: require.resolve('./e2e/global-teardown-docker.ts'),
});