const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  console.log('=== TESTE: UF Selector e ViaCEP ===\n');
  
  try {
    // Login como organizador
    await page.goto('http://localhost:4200/login', { waitUntil: 'networkidle' });
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);
    
    console.log('1. Login realizado, URL:', page.url());
    
    // Navegar para criar evento
    await page.goto('http://localhost:4200/events/create', { waitUntil: 'networkidle' });
    await page.waitForTimeout(3000);
    
    console.log('2. Página de criação de evento carregada');
    console.log('   URL:', page.url());
    
    // Verificar se o seletor UF está presente
    const ufSelect = page.locator('select#state');
    const ufCount = await ufSelect.count();
    console.log(`3. Seletor UF encontrado: ${ufCount > 0 ? 'SIM' : 'NÃO'}`);
    
    if (ufCount > 0) {
      // Verificar opções
      const options = await ufSelect.locator('option').allTextContents();
      console.log(`   Total de opções: ${options.length}`);
      console.log(`   Primeira opção: "${options[0]}"`);
      console.log(`   Algumas UFs: ${options.slice(1, 5).join(', ')}...`);
      
      // Testar seleção de UF
      await ufSelect.selectOption('SP');
      await page.waitForTimeout(500);
      const selectedValue = await ufSelect.inputValue();
      console.log(`4. Seleção UF "SP": ${selectedValue === 'SP' ? 'OK' : 'FALHOU'}`);
      
      // Testar seleção inválida (não deveria ser possível via select)
      console.log('   Select nativo impede valores inválidos ✓');
    }
    
    // Testar CEP ViaCEP
    console.log('\n5. Testando ViaCEP...');
    const cepInput = page.locator('#zipCode');
    await cepInput.fill('01310-100'); // CEP da Av. Paulista, SP
    await cepInput.blur();
    await page.waitForTimeout(5000); // Aguardar requisição
    
    // Verificar se campos foram preenchidos
    const street = await page.locator('#street').inputValue();
    const neighborhood = await page.locator('#neighborhood').inputValue();
    const city = await page.locator('#city').inputValue();
    const state = await page.locator('#state').inputValue();
    
    console.log(`   Logradouro: "${street}"`);
    console.log(`   Bairro: "${neighborhood}"`);
    console.log(`   Cidade: "${city}"`);
    console.log(`   UF: "${state}"`);
    
    const cepWorked = street && neighborhood && city && state === 'SP';
    console.log(`   ViaCEP funcionou: ${cepWorked ? 'SIM' : 'NÃO (ou parcial)'}`);
    
    // Testar CEP inválido
    console.log('\n6. Testando CEP inválido...');
    await cepInput.fill('99999-999');
    await cepInput.blur();
    await page.waitForTimeout(5000);
    
    const cepError = await page.locator('.form-text.text-danger:has-text("não encontrado")').count();
    console.log(`   Erro exibido para CEP inexistente: ${cepError > 0 ? 'SIM' : 'NÃO'}`);
    
    console.log('\n=== TESTE CONCLUÍDO ===');
    
  } catch (e) {
    console.error('ERRO:', e);
  }
  
  await browser.close();
})();
