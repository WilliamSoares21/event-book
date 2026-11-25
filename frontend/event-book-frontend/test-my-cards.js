const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  console.log('=== TESTE: Área de Cartões (My Cards) ===\n');
  
  try {
    // Login como comprador
    console.log('1. Login como Larissa (COMPRADOR)...');
    await page.goto('http://localhost:4200/login', { waitUntil: 'networkidle' });
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);
    console.log('   Login realizado, URL:', page.url());
    
    // Navegar para my-cards
    console.log('\n2. Navegando para /my-cards...');
    await page.goto('http://localhost:4200/my-cards', { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);
    console.log('   URL:', page.url());
    
    // Verificar título
    const title = await page.locator('h2').first().textContent();
    console.log(`   Título da página: "${title?.trim()}"`);
    
    // Verificar se está carregando ou vazio
    const loading = await page.locator('.spinner-border').count();
    const emptyState = await page.locator('text=Nenhum cartão cadastrado').count();
    const cardsGrid = await page.locator('.row.g-3').count();
    
    console.log(`   Estado de carregamento: ${loading > 0 ? 'SIM' : 'NÃO'}`);
    console.log(`   Estado vazio: ${emptyState > 0 ? 'SIM' : 'NÃO'}`);
    console.log(`   Grid de cartões: ${cardsGrid > 0 ? 'SIM' : 'NÃO'}`);
    
    // Testar botão cadastrar cartão
    console.log('\n3. Testando cadastro de cartão...');
    const createBtn = page.locator('button:has-text("Cadastrar Cartão")').first();
    await createBtn.click();
    await page.waitForTimeout(3000);
    
    // Verificar se cartão apareceu
    const cardsAfterCreate = await page.locator('.row.g-3 >> .card').count();
    console.log(`   Cartões após cadastro: ${cardsAfterCreate}`);
    
    if (cardsAfterCreate > 0) {
      // Verificar detalhes do primeiro cartão
      const firstCard = page.locator('.row.g-3 >> .card').first();
      const brand = await firstCard.locator('.fw-bold.fs-5').textContent();
      const lastFour = await firstCard.locator('.fw-bold.fs-5').textContent();
      const expiry = await firstCard.locator('small:has-text("Validade") + span').textContent();
      const totalLimit = await firstCard.locator('small:has-text("Limite Total") + span').textContent();
      const availableLimit = await firstCard.locator('small:has-text("Limite Disponível") + span').textContent();
      
      console.log(`   Marca/Últimos 4: ${brand?.trim()}`);
      console.log(`   Validade: ${expiry?.trim()}`);
      console.log(`   Limite Total: ${totalLimit?.trim()}`);
      console.log(`   Limite Disponível: ${availableLimit?.trim()}`);
      
      // Verificar badge de status
      const statusBadge = await firstCard.locator('.badge.rounded-pill').textContent();
      console.log(`   Status: ${statusBadge?.trim()}`);
      
      // Verificar botão desativar
      const deactivateBtn = await firstCard.locator('button:has-text("Desativar")').count();
      console.log(`   Botão Desativar: ${deactivateBtn > 0 ? 'SIM' : 'NÃO'}`);
      
      // Testar desativação
      console.log('\n4. Testando desativação de cartão...');
      await firstCard.locator('button:has-text("Desativar")').click();
      await page.waitForTimeout(3000);
      
      const statusAfterDeactivate = await firstCard.locator('.badge.rounded-pill').textContent();
      console.log(`   Status após desativar: ${statusAfterDeactivate?.trim()}`);
      
      const deactivateBtnAfter = await firstCard.locator('button:has-text("Desativar")').count();
      console.log(`   Botão Desativar após desativar: ${deactivateBtnAfter > 0 ? 'SIM (não deveria)' : 'NÃO (correto)'}`);
      
      const disabledBtn = await firstCard.locator('button:disabled:has-text("inativo")').count();
      console.log(`   Botão desabilitado "Cartão inativo": ${disabledBtn > 0 ? 'SIM' : 'NÃO'}`);
    }
    
    // Testar navegação via navbar
    console.log('\n5. Testando navegação via navbar...');
    await page.locator('button[data-bs-toggle="dropdown"]').click();
    await page.waitForTimeout(500);
    const myCardsLink = await page.locator('a.dropdown-item:has-text("Meus Cartões")').count();
    console.log(`   Link "Meus Cartões" no dropdown: ${myCardsLink > 0 ? 'SIM' : 'NÃO'}`);
    
    console.log('\n=== TESTE CONCLUÍDO ===');
    
  } catch (e) {
    console.error('ERRO:', e);
  }
  
  await browser.close();
})();
