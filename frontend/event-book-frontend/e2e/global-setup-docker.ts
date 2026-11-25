import { chromium } from '@playwright/test';

async function waitForService(url: string, name: string, maxRetries = 30): Promise<void> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        console.log(`✓ ${name} is ready at ${url}`);
        return;
      }
    } catch {
      // Service not ready yet
    }
    console.log(`Waiting for ${name}... (${i + 1}/${maxRetries})`);
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  throw new Error(`${name} at ${url} did not become ready in time`);
}

export default async function globalSetup() {
  console.log('=== Docker E2E Global Setup ===');
  console.log('Verifying Docker Compose services are available...');
  
  // Wait for frontend (Angular dev server in Docker)
  await waitForService('http://localhost:4200', 'Frontend (Angular)');
  
  // Wait for backend API
  await waitForService('http://localhost:8080/api/health', 'Backend (Spring Boot)');
  
  // Verify test users exist
  console.log('Verifying test users...');
  try {
    const letoResponse = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'leto@test.com', password: 'password123' })
    });
    if (!letoResponse.ok) throw new Error('Leto login failed');
    const letoData = await letoResponse.json();
    console.log(`✓ Leto (ORGANIZADOR) verified: ${letoData.user.name}`);
    
    const larissaResponse = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'larissa@test.com', password: 'password123' })
    });
    if (!larissaResponse.ok) throw new Error('Larissa login failed');
    const larissaData = await larissaResponse.json();
    console.log(`✓ Larissa (COMPRADOR) verified: ${larissaData.user.name}`);
    
  } catch (error) {
    throw new Error(`Test user verification failed: ${error}`);
  }
  
  console.log('=== All services verified - Ready for E2E tests ===');
}