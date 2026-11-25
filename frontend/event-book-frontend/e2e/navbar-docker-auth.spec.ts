import { test, expect } from '@playwright/test';

test.describe('Navbar - Docker Environment E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Limpar sessão antes de cada teste
    await page.goto('/');
    await page.evaluate(() => sessionStorage.clear());
    await page.reload();
  });

  test('Login como Leto (ORGANIZADOR) -> Navbar mostra Leto -> Dropdown abre com opções de organizador', async ({ page }) => {
    // Navegar para login
    await page.goto('/login');
    
    // Preencher credenciais de Leto
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    
    // Aguardar navegação para /events
    await page.waitForURL('/events');
    
    // Verificar que a Navbar mostra Leto
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toBeVisible();
    await expect(userButton).toContainText('Leto');
    
    // Clicar no botão do usuário para abrir dropdown
    await userButton.click();
    
    // Verificar dropdown aberto com opções para ORGANIZADOR
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    await expect(dropdown.locator('text=Meus Eventos')).toBeVisible();
    await expect(dropdown.locator('text=Criar Evento')).toBeVisible();
    await expect(dropdown.locator('text=Sair')).toBeVisible();
  });

  test('Login como Larissa (COMPRADOR) -> Navbar mostra Larissa -> Dropdown abre com opções de comprador', async ({ page }) => {
    await page.goto('/login');
    
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toBeVisible();
    await expect(userButton).toContainText('Larissa');
    
    await userButton.click();
    
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    await expect(dropdown.locator('text=Meus Ingressos')).toBeVisible();
    await expect(dropdown.locator('text=Meus Pedidos')).toBeVisible();
    await expect(dropdown.locator('text=Sair')).toBeVisible();
  });

  test('Refresh preserva sessão do Leto', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Leto');
    
    // Refresh da página
    await page.reload();
    
    // Verificar que Leto ainda está logado
    await expect(userButton).toContainText('Leto');
  });

  test('Refresh preserva sessão da Larissa', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Larissa');
    
    // Refresh da página
    await page.reload();
    
    // Verificar que Larissa ainda está logada
    await expect(userButton).toContainText('Larissa');
  });

  test('Troca de usuário: Login Leto -> Logout -> Login Larissa -> Navbar mostra Larissa', async ({ page }) => {
    // Login Leto (ORGANIZADOR)
    await page.goto('/login');
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    let userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Leto');
    
    // Logout
    await userButton.click();
    await page.locator('.dropdown-menu.show >> text=Sair').click();
    await page.waitForURL('/login');
    
    // Login Larissa (COMPRADOR)
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    // Verificar que agora mostra Larissa
    userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Larissa');
    
    // Verificar opções de COMPRADOR
    await userButton.click();
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    await expect(dropdown.locator('text=Meus Ingressos')).toBeVisible();
    await expect(dropdown.locator('text=Meus Pedidos')).toBeVisible();
  });

  test('Troca de usuário: Login Larissa -> Logout -> Login Leto -> Navbar mostra Leto', async ({ page }) => {
    // Login Larissa (COMPRADOR)
    await page.goto('/login');
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    let userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Larissa');
    
    // Logout
    await userButton.click();
    await page.locator('.dropdown-menu.show >> text=Sair').click();
    await page.waitForURL('/login');
    
    // Login Leto (ORGANIZADOR)
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    // Verificar que agora mostra Leto
    userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await expect(userButton).toContainText('Leto');
    
    // Verificar opções de ORGANIZADOR
    await userButton.click();
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    await expect(dropdown.locator('text=Meus Eventos')).toBeVisible();
    await expect(dropdown.locator('text=Criar Evento')).toBeVisible();
  });

  test('Logout limpa sessão e redireciona para /login', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await userButton.click();
    await page.locator('.dropdown-menu.show >> text=Sair').click();
    await page.waitForURL('/login');
    
    // Verificar que a Navbar voltou ao estado visitante
    await expect(page.locator('a[href="/login"]')).toContainText('Entrar');
    
    // Verificar que sessionStorage foi limpo
    const sessionData = await page.evaluate(() => sessionStorage.getItem('event-book-user'));
    expect(sessionData).toBeNull();
  });

  test('Dropdown fecha ao clicar fora (navbar brand)', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await userButton.click();
    
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    
    // Clicar fora do dropdown (no brand da navbar)
    await page.click('.navbar-brand');
    
    // Dropdown deve fechar
    await expect(dropdown).not.toBeVisible({ timeout: 5000 });
  });

  test('Dropdown fecha com tecla Escape', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'larissa@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await userButton.click();
    
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    
    // Focar no dropdown e pressionar Escape
    await dropdown.focus();
    await page.keyboard.press('Escape');
    
    // Dropdown deve fechar
    await expect(dropdown).not.toBeVisible({ timeout: 5000 });
  });

  test('Dropdown fecha ao clicar novamente no botão do usuário', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    await userButton.click();
    
    const dropdown = page.locator('.dropdown-menu.show');
    await expect(dropdown).toBeVisible();
    
    // Clicar novamente no botão do usuário
    await userButton.click();
    
    // Dropdown deve fechar
    await expect(dropdown).not.toBeVisible({ timeout: 5000 });
  });
});