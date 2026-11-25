const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: false });
  const page = await browser.newPage();
  
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', err => console.log('PAGE ERROR:', err.message));
  
  try {
    await page.goto('http://localhost:4200/login', { waitUntil: 'networkidle' });
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('/events');
    console.log('Logged in, navigated to /events');
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    console.log('User button text:', await userButton.textContent());
    
    await userButton.click();
    await page.waitForTimeout(1000);
    
    const dropdown = page.locator('.dropdown-menu');
    console.log('Dropdown exists:', await dropdown.count());
    console.log('Dropdown visible:', await dropdown.first().isVisible());
    console.log('Dropdown HTML:', await dropdown.first().innerHTML().catch(() => 'N/A'));
    
    // Check if .show class is present
    const dropdownShow = page.locator('.dropdown-menu.show');
    console.log('Dropdown with .show class:', await dropdownShow.count());
    console.log('Dropdown with .show visible:', await dropdownShow.first().isVisible().catch(() => false));
    
    // Check all dropdown menus
    const allDropdowns = page.locator('.dropdown-menu');
    for (let i = 0; i < await allDropdowns.count(); i++) {
      const el = allDropdowns.nth(i);
      console.log(`Dropdown ${i}: class="${await el.getAttribute('class')}", visible=${await el.isVisible()}`);
    }
    
    await page.waitForTimeout(5000);
  } catch (e) {
    console.error('Error:', e);
  }
  
  await browser.close();
})();
