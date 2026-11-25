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
    await page.waitForTimeout(5000);
    
    console.log('Current URL:', page.url());
    
    const userButton = page.locator('button[data-bs-toggle="dropdown"]').first();
    console.log('User button text:', await userButton.textContent());
    
    // Try clicking with force
    await userButton.click({ force: true });
    await page.waitForTimeout(2000);
    
    const dropdown = page.locator('.dropdown-menu');
    console.log('After click - Dropdown visible:', await dropdown.first().isVisible());
    
    // Check class
    for (let i = 0; i < await dropdown.count(); i++) {
      const el = dropdown.nth(i);
      console.log(`Dropdown ${i}: class="${await el.getAttribute('class')}", visible=${await el.isVisible()}`);
    }
    
    // Try clicking again
    await userButton.click({ force: true });
    await page.waitForTimeout(1000);
    
    console.log('After second click:');
    for (let i = 0; i < await dropdown.count(); i++) {
      const el = dropdown.nth(i);
      console.log(`Dropdown ${i}: class="${await el.getAttribute('class')}", visible=${await el.isVisible()}`);
    }
    
    // Try manual bootstrap toggle
    const result = await page.evaluate(() => {
      const btn = document.querySelector('button[data-bs-toggle="dropdown"]');
      if (btn && typeof bootstrap !== 'undefined') {
        const dropdown = new bootstrap.Dropdown(btn);
        dropdown.toggle();
        return 'toggled via bootstrap';
      }
      return 'bootstrap not available or button not found';
    });
    console.log('Manual bootstrap toggle:', result);
    
    await page.waitForTimeout(1000);
    
    for (let i = 0; i < await dropdown.count(); i++) {
      const el = dropdown.nth(i);
      console.log(`Dropdown ${i}: class="${await el.getAttribute('class')}", visible=${await el.isVisible()}`);
    }
    
    await page.waitForTimeout(5000);
  } catch (e) {
    console.error('Error:', e);
  }
  
  await browser.close();
})();
