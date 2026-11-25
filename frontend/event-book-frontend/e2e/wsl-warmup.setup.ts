import { chromium } from '@playwright/test';

/**
 * WSL2 (VM de ~4 GB): o dev server do Angular compila os chunks sob demanda no
 * primeiro acesso. Sem este aquecimento o primeiro teste paga a compilação fria
 * e estoura o timeout de 30s antes mesmo de chegar ao login.
 */
export default async function warmup() {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  try {
    await page.goto('http://localhost:4201/', { waitUntil: 'load', timeout: 180_000 });
    // networkidle pode nunca disparar com o websocket do HMR: o timeout é o teto.
    await page.waitForLoadState('networkidle', { timeout: 180_000 }).catch(() => undefined);
    // Inicializa o DispatcherServlet do backend (lazy) fora da janela dos testes.
    await page.request.get('http://localhost:8080/api/events?page=0&size=1').catch(() => undefined);
  } finally {
    await browser.close();
  }
}
