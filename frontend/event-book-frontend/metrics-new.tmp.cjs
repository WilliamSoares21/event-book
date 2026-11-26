const { chromium } = require('@playwright/test');
const fs = require('fs');
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const BOX = `(el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), bottom: Math.round(r.bottom) }; }`;

(async () => {
  const browser = await chromium.launch({ args: ['--disable-dev-shm-usage'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(45000);
  const metrics = {};

  await page.goto('http://localhost:4201/contact', { waitUntil: 'commit' });
  await page.waitForSelector('h2');
  await page.waitForTimeout(400);
  metrics.contactDesktop = await page.evaluate(`(() => { const box = ${BOX};
    return { viewportH: window.innerHeight, docH: document.documentElement.scrollHeight,
      footer: box(document.querySelector('app-footer footer')),
      main: box(document.querySelector('app-root main')),
      wrapper: box(document.querySelector('app-root .page-wrapper')),
      copyrightColor: getComputedStyle(document.querySelector('app-footer p')).color }; })()`);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  metrics.contactMobile = await page.evaluate(`(() => { const box = ${BOX};
    return { viewportH: window.innerHeight, docH: document.documentElement.scrollHeight,
      footer: box(document.querySelector('app-footer footer')) }; })()`);

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://localhost:4201/shop-events', { waitUntil: 'commit' });
  await page.waitForTimeout(2500);
  metrics.shopDesktop = await page.evaluate(`(() => { const box = ${BOX};
    const h1 = document.querySelector('h1');
    return { viewportH: window.innerHeight, docH: document.documentElement.scrollHeight,
      h1: box(h1), h1Color: h1 ? getComputedStyle(h1).color : null,
      banner: box(document.querySelector('.catalog-banner')),
      cards: document.querySelectorAll('.event-grid article.card').length,
      titles: [...document.querySelectorAll('.card-title')].map((t) => t.textContent.trim()) }; })()`);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(600);
  metrics.shopMobile = await page.evaluate(`(() => ({ innerW: window.innerWidth, scrollW: document.documentElement.scrollWidth, docH: document.documentElement.scrollHeight }))()`);

  fs.writeFileSync('/tmp/opencode/shots/after-new-metrics.json', JSON.stringify(metrics, null, 2));
  console.log(JSON.stringify(metrics, null, 2));
  await browser.close();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
