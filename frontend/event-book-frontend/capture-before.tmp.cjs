const { chromium } = require('@playwright/test');
const fs = require('fs');

const dir = '/tmp/opencode/shots';
fs.mkdirSync(dir, { recursive: true });

const event = {
  id: 'visual-fixture',
  organizerId: 'org-1',
  title: 'Conferência Tech 2026',
  description:
    'Um encontro de dois dias sobre engenharia de software, arquitetura, dados e produto. ' +
    'Palestras, trilhas práticas e espaço para networking com quem constrói tecnologia no Nordeste.',
  startsAt: '2026-12-10T12:00:00Z',
  endsAt: '2026-12-11T21:00:00Z',
  venueName: 'Centro de Convenções',
  street: 'Av. Beira Mar',
  number: '1000',
  neighborhood: 'Meireles',
  city: 'Fortaleza',
  state: 'CE',
  zipCode: '60165-121',
  location: 'Centro de Convenções, Fortaleza - CE',
  status: 'PUBLISHED',
  version: 0,
  coverImageUrl: 'https://images.nebula-labs.tech/events/3fa96a76-d7f1-4e19-871b-7b6a91120891.webp',
  availableTicketLots: [
    { id: 'l1', name: 'Lote Promocional - Ingresso VIP', unitPrice: 49.9, availableQuantity: 100 },
    { id: 'l2', name: 'Lote 1 - Pista', unitPrice: 100, availableQuantity: 5 },
    { id: 'l3', name: 'Lote 2 - Front Stage', unitPrice: 250.5, availableQuantity: 2 },
  ],
};

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const metrics = {};

  await page.route('**/api/events/visual-fixture', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(event) }),
  );

  await page.goto('http://localhost:4201/login', { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.fill('#email', 'lari@gmail.com');
  await page.fill('#password', '12345678');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/events', { timeout: 120000 });

  // ---------- Event details (desktop) ----------
  await page.goto('http://localhost:4201/shop-events/visual-fixture', { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForSelector('.event-card');
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${dir}/before-details-desktop.png`, fullPage: true });

  metrics.detailsDesktop = await page.evaluate(() => {
    const box = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
    };
    const table = document.querySelector('table');
    const ths = [...document.querySelectorAll('thead th')].map((th) => ({
      text: th.textContent.trim(), w: Math.round(th.getBoundingClientRect().width),
    }));
    return {
      content: box('.side-page-content'),
      card: box('.event-card'),
      image: box('.event-image'),
      body: box('.event-card .card-body'),
      table: table ? { w: Math.round(table.getBoundingClientRect().width), scrollW: table.parentElement.scrollWidth } : null,
      ths,
      bodyFontSize: getComputedStyle(document.querySelector('.event-card .card-body')).fontSize,
    };
  });

  const plus = page.locator('button[aria-label="Aumentar quantidade"]');
  await plus.nth(0).click();
  await plus.nth(0).click();
  await plus.nth(1).click();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${dir}/before-details-selected-desktop.png`, fullPage: true });

  await page.getByRole('button', { name: 'Comprar', exact: true }).click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/before-modal-desktop.png` });
  metrics.modalDesktop = await page.evaluate(() => {
    const d = document.querySelector('dialog.order-dialog');
    if (!d) return null;
    const r = d.getBoundingClientRect();
    return { w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.x), y: Math.round(r.y) };
  });
  await page.evaluate(() => document.querySelector('dialog.order-dialog')?.close());

  // ---------- Event details (mobile 390) ----------
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${dir}/before-details-mobile.png`, fullPage: true });
  metrics.detailsMobile = await page.evaluate(() => {
    const table = document.querySelector('table');
    const wrap = table?.parentElement;
    return {
      docScrollW: document.documentElement.scrollWidth,
      innerW: window.innerWidth,
      tableW: table ? Math.round(table.getBoundingClientRect().width) : null,
      wrapW: wrap ? Math.round(wrap.getBoundingClientRect().width) : null,
      wrapScrollW: wrap ? wrap.scrollWidth : null,
      stepperW: Math.round(document.querySelector('.input-group')?.getBoundingClientRect().width ?? 0),
    };
  });

  await page.getByRole('button', { name: 'Comprar', exact: true }).click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/before-modal-mobile.png` });
  await page.evaluate(() => document.querySelector('dialog.order-dialog')?.close());

  // ---------- Tickets list ----------
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://localhost:4201/tickets', { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${dir}/before-tickets-desktop.png`, fullPage: true });
  metrics.ticketsDesktop = await page.evaluate(() => {
    const cards = [...document.querySelectorAll('.card')];
    const first = cards[0]?.getBoundingClientRect();
    return {
      count: cards.length,
      firstCard: first ? { w: Math.round(first.width), h: Math.round(first.height) } : null,
      titleSize: cards[0] ? getComputedStyle(cards[0].querySelector('.card-title')).fontSize : null,
      codeOverflow: [...document.querySelectorAll('code')].some((c) => c.scrollWidth > c.clientWidth + 1),
    };
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/before-tickets-mobile.png`, fullPage: true });

  // ---------- Ticket details ----------
  const href = await page.locator('a[href^="/tickets/"]').first().getAttribute('href');
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`http://localhost:4201${href}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForSelector('.ticket-card');
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${dir}/before-ticket-details-desktop.png`, fullPage: true });
  metrics.ticketDetailsDesktop = await page.evaluate(() => {
    const qr = document.querySelector('.ticket-qr');
    const img = document.querySelector('.ticket-image');
    return {
      qr: qr ? { w: Math.round(qr.getBoundingClientRect().width), h: Math.round(qr.getBoundingClientRect().height) } : null,
      image: img ? { w: Math.round(img.getBoundingClientRect().width), h: Math.round(img.getBoundingClientRect().height) } : null,
      cardH: Math.round(document.querySelector('.ticket-card').getBoundingClientRect().height),
    };
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/before-ticket-details-mobile.png`, fullPage: true });

  console.log(JSON.stringify({ href, metrics }, null, 2));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
