const { chromium } = require('@playwright/test');
const fs = require('fs');

const BASE = 'http://127.0.0.1:4201';
const OUT = '/tmp/opencode/shots';
fs.mkdirSync(OUT, { recursive: true });

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} :: ${name}${detail ? ' :: ' + detail : ''}`);
}

async function metrics(page) {
  return page.evaluate(() => {
    const footer = document.querySelector('footer');
    const h1 = document.querySelector('h1');
    const p = footer ? footer.querySelector('p') : null;
    return {
      docH: document.documentElement.scrollHeight,
      viewportH: window.innerHeight,
      scrollW: document.documentElement.scrollWidth,
      clientW: document.documentElement.clientWidth,
      footerBottom: footer ? Math.round(footer.getBoundingClientRect().bottom + window.scrollY) : null,
      h1Color: h1 ? getComputedStyle(h1).color : null,
      h1Text: h1 ? h1.textContent.trim() : null,
      footerPColor: p ? getComputedStyle(p).color : null,
      cards: document.querySelectorAll('.event-grid article.card').length,
    };
  });
}

function pageResponse(events, totalPages = 1, totalElements = events.length) {
  return {
    content: events,
    page: 0,
    size: 10,
    totalElements,
    totalPages,
    first: true,
    last: totalPages <= 1,
  };
}

const mkEvent = (id, title, location = 'Fortaleza - CE') => ({
  id,
  title,
  startsAt: '2027-06-15T18:30:00Z',
  endsAt: '2027-06-15T21:30:00Z',
  location,
  status: 'PUBLISHED',
});

(async () => {
  const browser = await chromium.launch({ headless: true });
  const desktop = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await desktop.newPage();

  // ---------- 1. /contact desktop: footer no fim da página + contraste ----------
  await page.goto(`${BASE}/contact`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('footer');
  const contact = await metrics(page);
  check('contact: footer encostado no fim do documento',
    contact.footerBottom !== null && contact.footerBottom >= contact.docH - 2,
    `footerBottom=${contact.footerBottom} docH=${contact.docH}`);
  check('contact: sem scroll horizontal', contact.scrollW <= contact.clientW,
    `scrollW=${contact.scrollW} clientW=${contact.clientW}`);
  check('contact: texto do rodapé em branco (contraste)',
    contact.footerPColor === 'rgb(255, 255, 255)', `cor=${contact.footerPColor}`);
  await page.screenshot({ path: `${OUT}/after-contact-desktop.png`, fullPage: true, animations: 'disabled' });

  // ---------- 2. /shop-events desktop com API real ----------
  await page.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.event-grid article.card, .catalog-status');
  const shop = await metrics(page);
  check('shop-events: título visível (contraste)', shop.h1Color === 'rgb(255, 255, 255)',
    `cor=${shop.h1Color} texto="${shop.h1Text}"`);
  check('shop-events: sem scroll horizontal', shop.scrollW <= shop.clientW,
    `scrollW=${shop.scrollW} clientW=${shop.clientW}`);
  check('shop-events: sem espaço extra após o rodapé (sem scroll duplo)',
    shop.footerBottom !== null && Math.abs(shop.footerBottom - shop.docH) <= 2,
    `footerBottom=${shop.footerBottom} docH=${shop.docH} viewportH=${shop.viewportH}`);
  const titles = await page.locator('.event-grid article.card .card-title').allTextContents();
  check('shop-events: só eventos compráveis (API real)',
    titles.length === 1 && titles[0].includes('Nordeste Tech Summit'), `eventos=${JSON.stringify(titles)}`);
  await page.screenshot({ path: `${OUT}/after-shop-events-desktop.png`, fullPage: true, animations: 'disabled' });

  // ---------- 3. busca: debounce, 1 requisição por pausa ----------
  let requests = [];
  await page.route('**/api/events/available*', async route => {
    const url = new URL(route.request().url());
    requests.push({ q: url.searchParams.get('q'), page: url.searchParams.get('page') });
    await route.fulfill({ json: pageResponse([mkEvent('e1', 'Evento Alpha')]) });
  });
  await page.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.event-grid article.card');
  requests = [];

  const input = page.locator('#search-name');
  await input.pressSequentially('tech', { delay: 60 });
  await page.waitForTimeout(450);
  check('busca: exatamente 1 requisição por pausa', requests.length === 1,
    `requisições=${JSON.stringify(requests)}`);
  check('busca: envia o termo final', requests[0] && requests[0].q === 'tech',
    `q=${requests[0] && requests[0].q}`);

  // ---------- 4. resposta antiga é descartada ----------
  let call = 0;
  await page.unroute('**/api/events/available*');
  await page.route('**/api/events/available*', async route => {
    const url = new URL(route.request().url());
    const q = url.searchParams.get('q');
    call++;
    if (call === 1) {
      await new Promise(r => setTimeout(r, 1500));
      try {
        await route.fulfill({ json: pageResponse([mkEvent('antigo', 'Resposta Antiga')]) });
      } catch (_) { /* requisição abortada pelo switchMap */ }
      return;
    }
    await route.fulfill({ json: pageResponse([mkEvent('novo', 'Resposta Nova')]) });
  });
  await page.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.event-grid article.card');
  await page.locator('#search-name').fill('a');
  await page.waitForTimeout(350);
  await page.locator('#search-name').fill('ab');
  await page.waitForTimeout(1800);
  const rendered = await page.locator('.event-grid article.card .card-title').allTextContents();
  check('busca: resposta antiga não sobrescreve a nova',
    rendered.length === 1 && rendered[0].includes('Resposta Nova'), `renderizado=${JSON.stringify(rendered)}`);

  // ---------- 5. estado vazio ----------
  await page.unroute('**/api/events/available*');
  await page.route('**/api/events/available*', async route => {
    await route.fulfill({ json: pageResponse([]) });
  });
  await page.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await page.locator('#search-name').fill('zzz');
  await page.waitForTimeout(500);
  const emptyText = await page.locator('.catalog-status').innerText();
  check('busca: estado vazio com mensagem específica',
    emptyText.includes('Nenhum evento encontrado'), `texto="${emptyText.trim()}"`);

  // ---------- 6. paginação: filtrar volta para a página 0 ----------
  await page.unroute('**/api/events/available*');
  requests = [];
  await page.route('**/api/events/available*', async route => {
    const url = new URL(route.request().url());
    requests.push({ q: url.searchParams.get('q'), page: url.searchParams.get('page') });
    await route.fulfill({ json: pageResponse(
      Array.from({ length: 10 }, (_, i) => mkEvent(`p${i}`, `Evento ${i}`)), 3, 25) });
  });
  await page.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.event-grid article.card');
  await page.getByRole('button', { name: 'Próxima' }).click();
  await page.waitForTimeout(300);
  const pageAfterNext = requests.at(-1).page;
  await page.locator('#search-name').fill('te');
  await page.waitForTimeout(500);
  check('busca: filtrar reseta a página para 0',
    requests.at(-1).page === '0' && pageAfterNext === '1',
    `página após Próxima=${pageAfterNext}, após busca=${requests.at(-1).page}`);

  await desktop.close();

  // ---------- 7. mobile 390px ----------
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const mpage = await mobile.newPage();

  await mpage.goto(`${BASE}/contact`, { waitUntil: 'domcontentloaded' });
  await mpage.waitForSelector('footer');
  const mContact = await metrics(mpage);
  check('mobile contact: sem scroll horizontal', mContact.scrollW <= mContact.clientW,
    `scrollW=${mContact.scrollW} clientW=${mContact.clientW}`);
  check('mobile contact: footer presente', mContact.footerBottom !== null, `footerBottom=${mContact.footerBottom}`);
  await mpage.screenshot({ path: `${OUT}/after-contact-mobile.png`, fullPage: true, animations: 'disabled' });

  await mpage.goto(`${BASE}/shop-events`, { waitUntil: 'domcontentloaded' });
  await mpage.waitForSelector('.event-grid article.card, .catalog-status');
  const mShop = await metrics(mpage);
  check('mobile shop-events: sem scroll horizontal', mShop.scrollW <= mShop.clientW,
    `scrollW=${mShop.scrollW} clientW=${mShop.clientW}`);
  const boxes = await mpage.locator('.event-grid article.card').evaluateAll(nodes =>
    nodes.map(n => Math.round(n.getBoundingClientRect().width)));
  check('mobile shop-events: cards em coluna única',
    boxes.length > 0 && boxes.every(w => w > 300), `larguras=${JSON.stringify(boxes)}`);
  await mpage.screenshot({ path: `${OUT}/after-shop-events-mobile.png`, fullPage: true, animations: 'disabled' });

  await mobile.close();
  await browser.close();

  fs.writeFileSync(`${OUT}/e2e-results.json`, JSON.stringify(results, null, 2));
  const failed = results.filter(r => !r.ok);
  console.log(`\nTOTAL=${results.length} FALHAS=${failed.length}`);
  process.exit(failed.length ? 1 : 0);
})().catch(err => {
  console.error('ERRO FATAL:', err.message);
  process.exit(2);
});
