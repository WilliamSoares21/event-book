export default async function globalTeardown() {
  console.log('=== Docker E2E Global Teardown ===');
  console.log('Tests completed. Docker services continue running.');
  console.log('To stop services: docker compose down');
}