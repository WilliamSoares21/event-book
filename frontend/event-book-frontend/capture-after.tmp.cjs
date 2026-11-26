const { chromium } = require('@playwright/test');
const fs = require('fs');

const dir = '/tmp/opencode/shots';
fs.mkdirSync(dir, { recursive: true });
const log = (...a) => { console.log(new Date().toISOString().slice(11, 19), ...a); };

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

const tickets = [
  { id: 't1', eventTitle: 'Conferência Tech 2026', eventStartsAt: '2026-12-10T12:00:00Z', uniqueCode: 'EVBK-7Q2M-9XKD', status: 'VALID', ticketLotName: 'Lote Promocional - Ingresso VIP', unitPriceSnapshot: 49.9 },
  { id: 't2', eventTitle: 'Nordeste Tech Summit: Inteligência, Código e Futuro', eventStartsAt: '2027-01-22T18:00:00Z', uniqueCode: 'EVBK-3R8T-1PLM', status: 'VALID', ticketLotName: 'Pista', unitPriceSnapshot: 100.9 },
  { id: 't3', eventTitle: 'Festival Tech', eventStartsAt: '2026-11-15T15:01:27Z', uniqueCode: 'EVBK-9W4N-6HZQ', status: 'CHECKED_IN', ticketLotName: 'Lote 2 VIP - Tech Event', unitPriceSnapshot: 60 },
  { id: 't4', eventTitle: 'Aulão de Vestibular', eventStartsAt: '2026-11-21T03:00:00Z', uniqueCode: 'EVBK-5K1V-8DTB', status: 'CANCELLED', ticketLotName: 'Lote 1 - mais barato', unitPriceSnapshot: 50 },
];

const BOX = `(el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }; }`;

(async () => {
  const browser = await chromium.launch({ args: ['--disable-dev-shm-usage'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(45000);
  page.setDefaultNavigationTimeout(60000);
  const metrics = {};

  await page.route('**/api/events/visual-fixture', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(event) }),
  );
  await page.route('**/api/tickets/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(tickets) }),
  );

  log('login');
  await page.goto('http://localhost:4201/login', { waitUntil: 'commit' });
  await page.waitForSelector('#email');
  await page.fill('#email', 'lari@gmail.com');
  await page.fill('#password', '12345678');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/events', { timeout: 60000 });

  log('details desktop');
  await page.goto('http://localhost:4201/shop-events/visual-fixture', { waitUntil: 'commit' });
  await page.waitForSelector('.lot-table');
  await page.waitForTimeout(700);
  await page.screenshot({ path: `${dir}/after-details-desktop.png`, fullPage: true });
  metrics.detailsDesktop = await page.evaluate(`(() => { const box = ${BOX};
    const table = document.querySelector('.lot-table'); const wrap = table?.parentElement;
    return {
      content: box(document.querySelector('.side-page-content')),
      card: box(document.querySelector('.event-card')),
      image: box(document.querySelector('.event-image')),
      body: box(document.querySelector('.event-card .card-body')),
      table: table ? { w: Math.round(table.getBoundingClientRect().width), wrapW: Math.round(wrap.getBoundingClientRect().width) } : null,
      ths: [...document.querySelectorAll('.lot-table thead th')].map((th) => ({ t: th.textContent.trim(), w: Math.round(th.getBoundingClientRect().width), h: Math.round(th.getBoundingClientRect().height) })),
      total: box(document.querySelector('.order-total')),
    }; })()`);

  const plus = page.locator('button[aria-label="Aumentar quantidade"]');
  await plus.nth(0).click();
  await plus.nth(0).click();
  await plus.nth(1).click();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${dir}/after-details-selected-desktop.png`, fullPage: true });

  log('modal desktop');
  await page.getByRole('button', { name: 'Comprar', exact: true }).click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/after-modal-desktop.png` });
  metrics.modalDesktop = await page.evaluate(`(() => { const box = ${BOX};
    return { dialog: box(document.querySelector('dialog.order-dialog')), line: box(document.querySelector('.order-line')), total: box(document.querySelector('.order-dialog-total')) }; })()`);
  await page.evaluate(() => document.querySelector('dialog.order-dialog')?.close());

  log('details mobile');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${dir}/after-details-mobile.png`, fullPage: true });
  metrics.detailsMobile = await page.evaluate(`(() => { const box = ${BOX};
    const firstRow = document.querySelector('.lot-table tbody tr');
    return { docScrollW: document.documentElement.scrollWidth, innerW: window.innerWidth,
      card: box(document.querySelector('.event-card')), row: box(firstRow), stepper: box(document.querySelector('.lot-stepper')),
      total: box(document.querySelector('.order-total')),
      rowCells: [...firstRow.querySelectorAll('td')].map((td) => ({ label: td.dataset.label, ...box(td) })) }; })()`);

  await page.getByRole('button', { name: 'Comprar', exact: true }).click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/after-modal-mobile.png` });
  await page.evaluate(() => document.querySelector('dialog.order-dialog')?.close());

  log('tickets desktop');
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://localhost:4201/tickets', { waitUntil: 'commit' });
  await page.waitForSelector('.ticket-card-title');
  await page.waitForTimeout(500);
  await page.screenshot({ path: `${dir}/after-tickets-desktop.png`, fullPage: true });
  metrics.ticketsDesktop = await page.evaluate(`(() => { const box = ${BOX};
    const cards = [...document.querySelectorAll('.ticket-card')];
    return { count: cards.length, heights: cards.map((c) => Math.round(c.getBoundingClientRect().height)),
      first: box(cards[0]), title: box(cards[0]?.querySelector('.ticket-card-title')), code: box(cards[0]?.querySelector('.ticket-card-code')) }; })()`);

  log('tickets mobile');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/after-tickets-mobile.png`, fullPage: true });
  metrics.ticketsMobile = await page.evaluate(`(() => { const box = ${BOX};
    const sb = document.querySelector('app-menu-side-bar');
    return { docScrollW: document.documentElement.scrollWidth, innerW: window.innerWidth,
      card: box(document.querySelector('.ticket-card')), code: box(document.querySelector('.ticket-card-code')),
      sidebarWidth: sb ? Math.round(sb.getBoundingClientRect().width) : null }; })()`);

  log('ticket details desktop');
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://localhost:4201/tickets/t1', { waitUntil: 'commit' });
  await page.waitForSelector('.ticket-card');
  await page.waitForSelector('.ticket-qr-frame');
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${dir}/after-ticket-details-desktop.png`, fullPage: true });
  metrics.ticketDetailsDesktop = await page.evaluate(`(() => { const box = ${BOX};
    return { card: box(document.querySelector('.ticket-card')), image: box(document.querySelector('.ticket-image')),
      qr: box(document.querySelector('.ticket-qr')), code: box(document.querySelector('.ticket-code')),
      hasLot: document.body.textContent.includes('Lote Promocional') }; })()`);

  log('ticket details mobile');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${dir}/after-ticket-details-mobile.png`, fullPage: true });
  metrics.ticketDetailsMobile = await page.evaluate(`(() => { const box = ${BOX};
    return { docScrollW: document.documentElement.scrollWidth, innerW: window.innerWidth,
      card: box(document.querySelector('.ticket-card')), qr: box(document.querySelector('.ticket-qr')) }; })()`);

  fs.writeFileSync('/tmp/opencode/shots/after-metrics.json', JSON.stringify(metrics, null, 2));
  log('done');
  await browser.close();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
