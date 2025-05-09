import { test, expect } from '@playwright/test';

test('homepage has APIX AI Gateway title', async ({ page }) => {
  await page.goto('/en/dashboard');
  await expect(page).toHaveTitle(/APIX AI Gateway/);
});

test('can navigate to settings page', async ({ page }) => {
  await page.goto('/en/dashboard');
  await page.click('text=Settings');
  await expect(page).toHaveURL(/.*\/settings/);
});

test('can navigate to metrics page', async ({ page }) => {
  await page.goto('/en/dashboard');
  await page.click('text=Metrics');
  await expect(page).toHaveURL(/.*\/metrics/);
});

test('can navigate to AI models page', async ({ page }) => {
  await page.goto('/en/dashboard');
  await page.click('text=AI Models');
  await expect(page).toHaveURL(/.*\/ai-models/);
});

test('can navigate to AI routing page', async ({ page }) => {
  await page.goto('/en/dashboard');
  await page.click('text=AI Routing');
  await expect(page).toHaveURL(/.*\/ai-routing/);
});

test('can navigate to API keys page', async ({ page }) => {
  await page.goto('/en/dashboard');
  await page.click('text=API Keys');
  await expect(page).toHaveURL(/.*\/api-keys/);
});
