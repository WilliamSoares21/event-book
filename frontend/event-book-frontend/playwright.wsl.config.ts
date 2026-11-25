import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',

  // Execução sequencial para controlar memória
  fullyParallel: false,
  workers: 1,
  retries: 0,

  // Relatório leve
  reporter: 'list',
  // Aquece o dev server (compilação sob demanda) antes dos testes cronometrados.
  globalSetup: './e2e/wsl-warmup.setup.ts',

  // Diagnóstico preservado quando houver falhas
  use: {
    baseURL: 'http://localhost:4201',
    headless: true,

    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',

    ...devices['Desktop Chrome'],
  },

  // Somente Chromium
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],

  // Reutiliza servidor já iniciado
  webServer: {
    command: 'npm run start -- --port 4201',
    url: 'http://localhost:4201',
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
