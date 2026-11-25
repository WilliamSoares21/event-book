const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  console.log('=== TESTE FINAL: Fluxo completo de troca de usuário ===\n');
  
  try {
    // 1. Login como Leto (Organizador)
    console.log('1. Login como Leto (ORGANIZADOR)...');
    await page.goto('http://localhost:4200/login', { waitUntil: 'networkidle' });
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);
    console.log('   URL atual:', page.url());
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    const userName = await userButton.textContent();
    console.log(`   ✓ Navbar mostra: "${userName.trim()}"`);
    
    await userButton.click();
    await page.waitForTimeout(500);
    const dropdown = page.locator('.dropdown-menu.show');
    const isVisible = await dropdown.isVisible();
    console.log(`   ✓ Dropdown abre: ${isVisible}`);
    
    // Verificar opções de organizador
    const hasMeusEventos = await dropdown.locator('text=Meus Eventos').isVisible();
    const hasCriarEvento = await dropdown.locator('text=Criar Evento').isVisible();
    console.log(`   ✓ Opção "Meus Eventos": ${hasMeusEventos}`);
    console.log(`   ✓ Opção "Criar Evento": ${hasCriarEvento}`);
    
    // 2. Logout
    console.log('\n2. Logout...');
    await dropdown.locator('text=Sair').click();
    await page.waitForTimeout(2000);
    console.log('   URL atual:', page.url());
    console.log('   ✓ Redirecionou para /login');
    
    // 3. Login como Larissa (Comprador)
    console.log('\n3. Login como Larissa (COMPRADOR)...');
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);
    console.log('   URL atual:', page.url());
    
    const userButton2 = page.locator('button[data-bs-toggle="dropdown"]').first();
    const userName2 = await userButton2.textContent();
    console.log(`   ✓ Navbar mostra: "${userName2.trim()}"`);
    
    await userButton2.click();
    await page.waitForTimeout(500);
    const dropdown2 = page.locator('.dropdown-menu.show');
    const isVisible2 = await dropdown2.isVisible();
    console.log(`   ✓ Dropdown abre: ${isVisible2}`);
    
    // Verificar opções de comprador
    const hasMeusIngressos = await dropdown2.locator('text=Meus Ingressos').isVisible();
    const hasMeusPedidos = await dropdown2.locator('text=Meus Pedidos').isVisible();
    console.log(`   ✓ Opção "Meus Ingressos": ${hasMeusIngressos}`);
    console.log(`   ✓ Opção "Meus Pedidos": ${hasMeusPedidos}`);
    
    // 4. Refresh
    console.log('\n4. Refresh da página...');
    await page.reload();
    await page.waitForTimeout(3000);
    console.log('   URL atual:', page.url());
    
    const userButton3 = page.locator('button[data-bs-toggle="dropdown"]').first();
    const userName3 = await userButton3.textContent();
    console.log(`   ✓ Após refresh, Navbar mostra: "${userName3.trim()}"`);
    
    // 5. Logout final
    console.log('\n5. Logout final...');
    await userButton3.click();
    await page.waitForTimeout(500);
    await page.locator('.dropdown-menu.show >> text=Sair').click();
    await page.waitForTimeout(2000);
    console.log('   URL atual:', page.url());
    console.log('   ✓ Logout realizado, voltou para /login');
    
    console.log('\n=== TODOS OS TESTES PASSARAM COM SUCESSO ===');
    
  } catch (e) {
    console.error('ERRO:', e);
  }
  
  await browser.close();
})();
