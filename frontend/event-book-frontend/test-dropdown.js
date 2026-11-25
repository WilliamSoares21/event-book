const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: false });
  const page = await browser.newPage();
  
  // Enable console logging
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', err => console.log('PAGE ERROR:', err.message));
  
  try {
    await page.goto('http://localhost:4200/login', { waitUntil: 'networkidle' });
    console.log('Loaded login page');
    
    // Fill login form
    await page.fill('#email', 'leto@test.com');
    await page.fill('#password', 'password123');
    await page.click('button[type="submit"]');
    
    // Wait for navigation
    await page.waitForURL('/events');
    console.log('Logged in, navigated to /events');
    
    // Check if bootstrap is loaded
    const bootstrapLoaded = await page.evaluate(() => typeof bootstrap !== 'undefined');
    console.log('Bootstrap loaded:', bootstrapLoaded);
    
    // Check dropdown elements
    const userButton = await page.locator('button[data-bs-toggle="dropdown"]').first();
    console.log('User button exists:', await userButton.count());
    console.log('User button text:', await userButton.textContent());
    
    if (await userButton.count() > 0) {
      await userButton.click();
      await page.waitForTimeout(1000);
      
      const dropdown = page.locator('.dropdown-menu.show');
      console.log('Dropdown visible:', await dropdown.isVisible());
      console.log('Dropdown HTML:', await dropdown.innerHTML());
    }
    
    await page.waitForTimeout(5000);
  } catch (e) {
    console.error('Error:', e);
  }
  
  await browser.close();
})();
